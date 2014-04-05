package net.fluxo.dd

import java.net.URL
import java.io.File
import org.json.simple.{JSONValue, JSONArray}
import org.apache.commons.io.FilenameUtils
import net.fluxo.dd.dbo.YIFYSearchResult

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
	def procListMovie(page: Int, quality:Int, rating: Int, externalIP: String, port: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/list.json?limit=15")
		if (quality <= 3 && quality >= 0) request.append("&quality=").append(quality  match {
			case 0 => "ALL"
			case 1 => "720p"
			case 2 => "1080p"
			case 3 => "3D"
		})
		if (page > 0) request.append("&set=" + page)
		if (rating >= 0 && rating <= 9) request.append("&rating=" + rating)
		// send the request...
		val response = OUtils crawlServer (request toString())
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return "ERR NO LIST"
		processImages(response, externalIP, port)
	}

	def procMovieDetails(id: Int, externalIP:String, port: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/movie.json?id=").append(id)
		val response = OUtils crawlServer (request toString())
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return "ERR MOVIE NOT FOUND"
		processScreenshotImages(response, externalIP, port)
	}

	def procYIFYCache(page: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/list.json?limit=50")
		if (page > 1) request append "&set=" append page
		val response = OUtils crawlServer (request toString())
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return  "ERR NO LIST"
		response
	}

	def procYIFYSearch(term: String): String = {
		val searchString = term replaceAllLiterally("%20", " ")
		val searchResult = DbControl ycQueryMoviesByTitle searchString
		val yifySearchResult = new YIFYSearchResult
		if ((searchResult length) > 0) {
			yifySearchResult.MovieCount_:(searchResult length)
			val request = new StringBuilder
			for (x <- searchResult) {
				request setLength 0
				request append "http://yts.re/api/movie.json?id=" append x.MovieID
				val response = OUtils crawlServer (request toString())
				yifySearchResult AddToMovieList (OUtils stringToMovieObject response)
			}
		}
		OUtils YIFYSearchResultToJSON yifySearchResult
	}

	private def processScreenshotImages(content: String, externalIP: String, port: Int): String = {
		var newContent = content
		val jsObj = JSONValue.parseWithException(content).asInstanceOf[org.json.simple.JSONObject]
		val arrKeys = Array("MediumCover", "MediumScreenshot1", "MediumScreenshot2", "MediumScreenshot3")

		for (x <- arrKeys) {
			val sc = jsObj.get(x).toString
			var newSc = sc replaceAllLiterally("\\/", "/")
			val path = new URL(newSc).getPath
			val dirname = "." + FilenameUtils.getFullPath(path)
			val dir = new File(dirname)
			if (!(dir exists())) dir mkdirs()
			val localFile = new File(dirname + FilenameUtils.getName(path))
			if (!(localFile exists())) new Thread(new WgetImage(newSc, dirname)) start()
			if (!externalIP.equals("127.0.0.1")) {
				val oldServer = new URL(newSc).getAuthority
				newSc = newSc replace(oldServer, externalIP + ":" + port)
				newSc = newSc replaceAllLiterally("/", "\\/")
				val oldSc = sc replaceAllLiterally("/", "\\/")
				if ((newContent indexOf oldSc) > -1) newContent = newContent replace(oldSc, newSc)
			}
		}
		newContent
	}

	private def processImages(content: String, externalIP: String, port: Int): String = {
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
			val dirName = "." + FilenameUtils.getFullPath(path)
			val dir = new File(dirName)
			if (!dir.exists()) {
				dir.mkdirs()
			}
			// fetch the image and put it inside our new directory (wget -P ./location)
			val localFile = new File(dirName + FilenameUtils.getName(path))
			if (!(localFile exists())) new Thread(new WgetImage(newCoverImage, dirName)).start()
			// remodel our image url into http://<our-outside-ip>/.....
			if (!externalIP.equals("127.0.0.1")) {
				val oldServer = new URL(newCoverImage).getAuthority
				newCoverImage = newCoverImage.replace(oldServer, externalIP + ":" + port)
				// and re-encode the forward slash to json forward slash
				newCoverImage = newCoverImage replaceAllLiterally("/", "\\/")
				val oldcoverImage = coverImage replaceAllLiterally("/", "\\/")
				// reinject the remodelled url back into the text
				if (newContent.indexOf(oldcoverImage) > -1) newContent = newContent replace(oldcoverImage, newCoverImage)
			}
		}
		newContent
	}

	class WgetImage(url: String, location: String) extends Runnable {
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "--directory-prefix=" + location, url) start()
			wgetProc waitFor()
		}
	}

}

object YIFYP extends YIFYProcessor
