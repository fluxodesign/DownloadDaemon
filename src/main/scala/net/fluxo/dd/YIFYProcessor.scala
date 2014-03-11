package net.fluxo.dd

import java.net.MalformedURLException
import org.apache.log4j.Level
import java.io.{InputStreamReader, BufferedReader, IOException}
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.json.simple.JSONArray

/**
 * User: Ronald Kurniawan (viper)
 * Date: 11/03/14
 * Time: 10:06 AM
 *
 */
class YIFYProcessor {

	/*
		quality: 0 = ALL, 1 = 720p, 2 = 1080p, 3 = 3D
		page: page to request
        rating: minimum rating to request, 0 - 9, default 0 (ALL)
	 */
	def procListMovie(page: Int, quality:Int, rating: Int): String = {
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
			processImages(response.toString())
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
		val jsObj = content.asInstanceOf[org.json.simple.JSONObject]
		val jsArray = jsObj.get("MovieList").asInstanceOf[JSONArray]
		val iterator = jsArray.iterator()
		while (iterator.hasNext) {
			val coverImage = iterator.next().asInstanceOf[org.json.simple.JSONObject].get("CoverImage")
			// now we get our "raw" image url; we need to decode json forward slash to simple forward slash
			val newCoverImage = coverImage.toString.replaceAllLiterally("\\/", "/")
			// DEBUG
			LogWriter.writeLog("newCoverImage: " + newCoverImage, Level.INFO)
			// now we need to analyse the url, create directory related to this url in our directory
			// fetch the image and put it inside our new directory
			// remodel our image url into http://<our-outside-ip/.....
			// and re-encode the forward slash to json forward slash
			// reinject the remodelled url back into the text
		}
		content
	}
}

object YIFYP extends YIFYProcessor