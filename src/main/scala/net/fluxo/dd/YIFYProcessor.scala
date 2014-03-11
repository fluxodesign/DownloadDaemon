package net.fluxo.dd

import java.net.{URL, MalformedURLException}
import org.apache.log4j.Level
import java.io.{File, InputStreamReader, BufferedReader, IOException}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.json.simple.{JSONValue, JSONArray}
import org.apache.commons.io.FilenameUtils

/**
 * User: Ronald Kurniawan (viper)
 * Date: 11/03/14
 * Time: 10:06 AM
 *
 */
class YIFYProcessor {

	var externalIP: String = "0"

	/*
		quality: 0 = ALL, 1 = 720p, 2 = 1080p, 3 = 3D
		page: page to request
        rating: minimum rating to request, 0 - 9, default 0 (ALL)
	 */
	def procListMovie(page: Int, quality:Int, rating: Int): String = {
		if (externalIP.equals("0")) {
			new Thread(new WgetExternalIP).start()
		}
		val request: StringBuilder = new StringBuilder("http://yts.re/api/list.json?limit=15")
		val response = new StringBuilder
		if (quality <= 3 && quality >= 0) request.append("&quality=").append(quality  match {
			case 0 => "ALL"
			case 1 => "720p"
			case 2 => "1080p"
			case 3 => "3D"
		})
		if (page > 0) request.append("&set=" + page)
		if (rating >= 0 && rating <= 9) request.append("&rating=" + rating)
		// send the request...
		try {
			val htClient = HttpClientBuilder.create().build()
			val htGet = new HttpGet(request.toString())
			htGet.addHeader("User-Agent", "FluxoAgent/0.1")
			val htResponse = htClient.execute(htGet)
			val br = new BufferedReader(new InputStreamReader(htResponse.getEntity.getContent))
			var line = br.readLine()
			while (line != null) {
				response.append(line)
				line = br.readLine()
			}
			val count = processImages(response.toString())
			response.clear()
			response.append(count)
			br.close()
			htClient.close()
		} catch {
			case mue: MalformedURLException =>
				LogWriter.writeLog("URL " + request.toString() + " is malformed", Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(mue), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
		response.toString()
	}

	def procMovieDetails(id: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/movie.json?id=").append(id)
		val response = new StringBuilder
		try {
			val htClient = HttpClientBuilder.create().build()
			val htGet = new HttpGet(request.toString() + id)
			htGet.addHeader("User-Agent", "FluxoAgent/0.1")
			val htResponse = htClient.execute(htGet)
			val br = new BufferedReader(new InputStreamReader(htResponse.getEntity.getContent))
			var line = br.readLine()
			while (line != null) {
				response.append(line)
				line = br.readLine()
			}
			br.close()
			htClient.close()
		} catch {
			case mue: MalformedURLException =>
				LogWriter.writeLog("URL " + request.toString() + " is malformed", Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(mue), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
		response toString()
	}

	private def processImages(content: String): String = {
		var newContent = content
		val jsObj = JSONValue.parseWithException(content).asInstanceOf[org.json.simple.JSONObject]
		val jsArray = jsObj.get("MovieList").asInstanceOf[JSONArray]
		val iterator = jsArray.iterator()
		while (iterator.hasNext) {
			val coverImage = iterator.next().asInstanceOf[org.json.simple.JSONObject].get("CoverImage").toString
			// now we get our "raw" image url; we need to decode json forward slash to simple forward slash
			var newCoverImage = coverImage.replaceAllLiterally("\\/", "/")
			// now we need to analyse the url, create directory related to this url in our directory
			val path = new URL(newCoverImage).getPath
			val dirName =FilenameUtils.getFullPath(path)
			val dir = new File(dirName)
			if (!dir.exists()) dir.mkdir()
			// fetch the image and put it inside our new directory (wget -P ./location)
			new Thread(new WgetImage(newCoverImage, dirName)).start()
			// remodel our image url into http://<our-outside-ip>/.....
			val oldServer = new URL(newCoverImage).getAuthority
			newCoverImage = newCoverImage.replace(oldServer, externalIP)
			// and re-encode the forward slash to json forward slash
			newCoverImage = newCoverImage.replaceAllLiterally("/", "\\/")
			// reinject the remodelled url back into the text
			// DEBUG
			System.out.println("newCoverImage: " + newCoverImage)
			newContent = newContent.replace(coverImage, newCoverImage)
		}
		newContent
	}

	class WgetImage(url: String, location: String) extends Runnable {
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "-P ./" + location, url).start()
			wgetProc.waitFor()
		}
	}

	class WgetExternalIP() extends Runnable {
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "-q", "-O", "- http://myexternalip.com/raw").redi
			wgetProc.re
			wgetProc.waitFor()
			val br = new BufferedReader(new InputStreamReader(wgetProc.getInputStream))
			externalIP = br.readLine()
			br.close()

		}
	}
}

object YIFYP extends YIFYProcessor