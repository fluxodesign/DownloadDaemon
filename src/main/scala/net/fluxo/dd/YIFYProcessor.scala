/*
 * YIFYProcessor.scala
 *
 * Copyright (c) 2014 Ronald Kurniawan. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.fluxo.dd

import java.io.File
import java.net.URL

import net.fluxo.dd.dbo.{YIFYCache, YIFYSearchResult}
import org.apache.commons.io.FilenameUtils
import org.apache.log4j.Level
import org.json.simple.parser.JSONParser
import org.json.simple.{JSONArray, JSONObject, JSONValue}

/**
 * This class deals with YIFY's web service, particularly with listing, acquiring and caching movie details, including
 * the screenshots of a movie, so user can access the details from our system.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 11/03/14
 */
class YIFYProcessor {

	/**
	 * Return the list of movies available from YIFY site. The list can be filtered by page (15 items per page), movie quality
	 * and IMDB rating.
	 *
	 * @param page the page number to display (starting from 1)
	 * @param quality filter the quality of movies to display (0 = ALL, 1 = 720p movies only, 2 = 1080p movies only, 3 = 3D movies only)
	 * @param rating IMDB rating; specifying 0 will display ALL movies, while specifying 5 for e.g., will display only movies
	 *               with IMDB rating of 5.0 and above
	 * @param externalIP the local system's external IP address
	 * @param port port number to which the embedded Jetty server is bound to
	 * @return the list of movies on a particular page from YIFY site, with all cover image URLs re-addressed to our site
	 */
	def procListMovie(page: Int, quality:Int, rating: Int, externalIP: String, port: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/v2/list_movies.json?limit=15")
		if (quality <= 3 && quality >= 0) request append "&quality=" append(quality  match {
			case 0 => "ALL"
			case 1 => "720p"
			case 2 => "1080p"
			case 3 => "3D"
		})
		if (page > 0) request append("&page=" + page)
		if (rating >= 0 && rating <= 9) request append("&minimum_rating=" + rating)
		// send the request...
		val response = OUtils crawlServer (request toString())
		checkEntryWithYIFYCache(response)
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return "ERR NO LIST"
		processImages(response, externalIP, port)
	}

	/**
	 * Return details of a particular movie from YIFY site.
	 *
	 * @param id ID of the movie
	 * @param externalIP the local system's external IP address
	 * @param port port number to which the embedded Jetty server is bound to
	 * @return details of a movie from YIFY site, with screenshot images re-addressed to our site
	 */
	def procMovieDetails(id: Int, externalIP:String, port: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/v2/movie_details.json?movie_id=") append id
		request append "&with_images=true&with_cast=true"
		val response = OUtils crawlServer (request toString())
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return "ERR MOVIE NOT FOUND"
		processScreenshotImages(response, externalIP, port)
	}

	/**
	 * Request a page from YIFY site, 50 items at a time
	 * @param page page number to request
	 * @return response from YIFY site
	 */
	def procYIFYCache(page: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/v2/list_movies.json?limit=50")
		if (page > 1) request append "&page=" append page
		val response = OUtils crawlServer (request toString())
		if ((response indexOf "status") > -1 && (response indexOf "fail") > -1) return  "ERR NO LIST"
		response
	}

	/**
	 * Process the search request from user. As YIFY does not provide search capabilities on it's public web service,
	 * we need to provide it from our cache database. Search is performed only on titles.
	 *
	 * @param term search term
	 * @return returns a JSON object containing the results of the search
	 */
	def procYIFYSearch(term: String): String = {
		val searchString = term replaceAllLiterally("%20", " ")
		val searchResult = DbControl ycQueryMoviesByTitle searchString
		val yifySearchResult = new YIFYSearchResult
		if ((searchResult length) > 0) {
			yifySearchResult.MovieCount_:(searchResult length)
			val request = new StringBuilder
			for (x <- searchResult) {
				request setLength 0
				request append "http://yts.re/api/v2/movie_details.json?movie_id=" append x.MovieID
				request append "&with_images=true"
				val response = OUtils crawlServer (request toString())
				yifySearchResult AddToMovieList (OUtils stringToMovieObject response)
			}
		}
		OUtils YIFYSearchResultToJSON yifySearchResult
	}

	/**
	 * Process results returned from YIFY site by changing the URLs of screenshot images to point to our site.
	 * @param content original string returned by YIFY site
	 * @param externalIP the local system's external IP address
	 * @param port port number where the embedded Jetty server is bound to
	 * @return response from YIFY site, with all screenshot image URLs re-addressed to point to our site
	 */
	private def processScreenshotImages(content: String, externalIP: String, port: Int): String = {
		var newContent = content
		val jsObj = (JSONValue parseWithException content).asInstanceOf[org.json.simple.JSONObject]
		val arrKeys = Array("MediumCover", "MediumScreenshot1", "MediumScreenshot2", "MediumScreenshot3")

		for (x <- arrKeys) {
			val sc = (jsObj get x).toString
			var newSc = sc replaceAllLiterally("\\/", "/")
			val path = new URL(newSc).getPath
			val dirname = "." + (FilenameUtils getFullPath path)
			val dir = new File(dirname)
			if (!(dir exists())) dir mkdirs()
			val localFile = new File(dirname + (FilenameUtils getName path))
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

	/**
	 * Process results returned from YIFY site by changing the URLs of cover images to point to our site.
	 * @param content original string returned by YIFY site
	 * @param externalIP the local system's external IP address
	 * @param port port number where embedded Jetty server is bound to
	 * @return response from YIFY site, with all cover image URLs re-addressed to point to our site
	 */
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
				newCoverImage = newCoverImage replace(oldServer, externalIP + ":" + port)
				// and re-encode the forward slash to json forward slash
				newCoverImage = newCoverImage replaceAllLiterally("/", "\\/")
				val oldcoverImage = coverImage replaceAllLiterally("/", "\\/")
				// reinject the remodelled url back into the text
				if (newContent.indexOf(oldcoverImage) > -1) newContent = newContent replace(oldcoverImage, newCoverImage)
			}
		}
		newContent
	}

	/**
	 * Checks whether entries returned from YIFY site already exists in our database; insert to db if new.
	 *
	 * @param raw string response from YIFY site
	 */
	private def checkEntryWithYIFYCache(raw: String) {
		val jsonParser = new JSONParser
		try {
			val obj = (jsonParser parse raw).asInstanceOf[JSONObject]
			val iterator = (obj get "movies").asInstanceOf[JSONArray] iterator()
			while (iterator.hasNext) {
				val o = (iterator next()).asInstanceOf[JSONObject]
				val yifyCache = new YIFYCache
				yifyCache.MovieID_:((o get "id").asInstanceOf[Long])
                LogWriter writeLog ("--:id = " + yifyCache.MovieID, Level.DEBUG)
				yifyCache.MovieTitle_:((o get "title").asInstanceOf[String])
                LogWriter writeLog ("--:title = " + yifyCache.MovieTitle, Level.DEBUG)
				yifyCache.MovieYear_:((o get "year").asInstanceOf[Long])
                LogWriter writeLog ("--:year = " + yifyCache.MovieYear, Level.DEBUG)
				yifyCache.MovieCoverImage_:((o get "medium_cover_image").asInstanceOf[String])
                LogWriter writeLog("--:cover = " + yifyCache.MovieCoverImage, Level.DEBUG)
				val torrentInfo = (o get "torrents").asInstanceOf[JSONArray] iterator()
				if (torrentInfo hasNext) {
					val torInfo = (torrentInfo next()).asInstanceOf[JSONObject]
					yifyCache.MovieQuality_:((torInfo get "quality").asInstanceOf[String])
                    LogWriter writeLog ("--:quality = " + yifyCache.MovieQuality, Level.DEBUG)
					yifyCache.MovieSize_:((torInfo get "size").asInstanceOf[String])
                    LogWriter writeLog("--:size = " + yifyCache.MovieSize, Level.DEBUG)
				}

				if (!(DbControl ycQueryMovieID(yifyCache MovieID))) {
					DbControl ycInsertNewData yifyCache
				}
			}
		} catch {
			case jse: Exception =>
				LogWriter writeLog("Error parsing JSON from YIFY movie list", Level.ERROR)
				LogWriter writeLog(jse.getMessage, Level.ERROR)
		}
	}

	/**
	 * This class runs a "wget" process to obtain an image and store it on our cache dir.
	 *
	 * @param url the URL of the image
	 * @param location local directory to store the image
	 *
	 * @author Ronald Kurniawan (viper)
	 * @version 0.4.5, 11/03/14
	 */
	class WgetImage(url: String, location: String) extends Runnable {
		/**
		 * Run a "wget" Process and wait until the Process exits.
		 */
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "--directory-prefix=" + location, url) start()
			wgetProc waitFor()
		}
	}

}

/**
 * Singleton object for YIFYProcessor
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 11/03/14
 */
object YIFYP extends YIFYProcessor
