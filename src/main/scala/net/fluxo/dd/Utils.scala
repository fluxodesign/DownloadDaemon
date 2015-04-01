/*
 * Utils.scala
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

import java.io._
import java.net._
import java.security.MessageDigest
import java.util
import java.util.zip.GZIPInputStream
import java.util.{Properties, Random}

import net.fluxo.dd.dbo.{Config, MovieObject, YIFYSearchResult}
import org.apache.commons.io.FileUtils
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.log4j.Level
import org.apache.xmlrpc.XmlRpcException
import org.apache.xmlrpc.client.{XmlRpcClient, XmlRpcClientConfigImpl}
import org.apache.xmlrpc.common.{TypeFactoryImpl, XmlRpcController, XmlRpcStreamConfig}
import org.apache.xmlrpc.serializer.{StringSerializer, TypeSerializer}
import org.json.simple.parser.JSONParser
import org.json.simple.{JSONArray, JSONObject, JSONValue}
import org.xml.sax.{ContentHandler, SAXException}

import scala.collection.JavaConversions._
import scala.util.control.Breaks._

/**
 * This class contains methods that can be called from anywhere in the application.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 15/03/14
 */
class Utils {

	private var _config: Option[Config] = None
	private val _randomizer: Random = new Random(System currentTimeMillis())
	private var _externalIP: Option[String] = Some("127.0.0.1")

	/**
	 * Return the externally-accessible IP address of the local system.
	 *
	 * @return IP address of local system, or loop address
	 */
	def ExternalIP: String = { _externalIP.getOrElse("127.0.0.1") }

	/**
	 * Setter method.
	 *
	 * @param value string value of externally-accesible IP address of the local system
	 */
	def ExternalIP_:(value: String) { _externalIP = Some(value) }

	/**
	 * Return the <code>Config</code> object.
	 *
	 * @return a <code>net.fluxo.dd.dbo.Config</code> object
	 */
	def readConfig: Config = {
		if (_config.isEmpty) _config = Some(readConfiguration)
		_config.orNull
	}

	/**
	 * Read the configuration file (dd.properties) into <code>net.fluxo.dd.dbo.Config</code> object.
	 *
	 * @return a <code>net.fluxo.dd.dbo.Config</code> object
	 */
	private def readConfiguration: Config = {
		val prop: Properties = new Properties
		var cfg: Config = new Config
		try {
			prop.load(new FileInputStream("./dd.properties"))
			cfg.RPCPort_=(java.lang.Integer.parseInt(prop getProperty "rpc_port"))
			cfg.RPCLimit_=(java.lang.Integer.parseInt(prop getProperty "rpc_limit"))
			cfg.HTTPSPort_=(java.lang.Integer.parseInt(prop getProperty "https_port"))
			cfg.HTTPPort_=(java.lang.Integer.parseInt(prop getProperty "http_port"))
			cfg.XMPPProvider_=(prop getProperty "xmpp_provider")
			cfg.XMPPAccount_=(prop getProperty "xmpp_account")
			cfg.XMPPPassword_=(prop getProperty "xmpp_password")
			cfg.DownloadDir_=(prop getProperty "download_dir")
			cfg.SSLKeystore_=(prop getProperty "ssl_keystore")
			cfg.SSLKeystorePassword_=(prop getProperty "ssl_keystore_password")
			cfg.SSLKeymanagerPassword_=(prop getProperty "ssl_keymanager_password")
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error reading properties file dd.properties", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
		cfg
	}

	/**
	 * Check whether all ports slated for aria2 processes are free.
	 *
	 * @return true if all ports are unbound; false otherwise
	 */
	def allPortsFree: Boolean = {
		var ret: Boolean = true
		breakable {
			for (x <- readConfig.RPCPort until readConfig.RPCPort + readConfig.RPCLimit) {
				if (portInUse(x)) {
					ret = false
					break()
				}
			}
		}
		ret
	}

	/**
	 * Delete the specified file from filesystem. If file is a directory, this method recursively deletes
	 * all the content before deleting the directory.
	 *
	 * @param file <code>File</code> object to delete
	 * @see java.util.File
	 */
	def deleteFile(file: File) {
		try {
			if (file.isDirectory) {
				if ((file list() length) == 0) file delete()
				else {
					val fileList = file list()
					for (fl <- fileList) {
						val f = new File(file, fl)
						deleteFile(f)
					}
					if ((file list() length) == 0) file delete()
				}
			} else {
				file delete()
			}
		} catch {
			case ioe: IOException =>
				LogWriter writeLog("Error deleting file " + file.getName, Level.ERROR)
				LogWriter writeLog(ioe.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ioe, Level.ERROR)
		}
	}

	/**
	 * Create a new directory "uridir" to store input files for aria2 process.
	 */
	def createUriDir() {
		try {
			val uriDir = new File("uridir")
			if (!(uriDir exists)) FileUtils forceMkdir uriDir
		} catch {
			case e: Exception =>
				LogWriter writeLog("Error creating uri directory", Level.ERROR)
				LogWriter writeLog(e.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
		}
	}

	/**
	 * Create a new input file for aria2 process.
	 *
	 * @param gid unique ID for the download
	 * @param uri target URL to download
	 * @return true if everything went smoothly; false otherwise
	 */
	def createUriFile(gid: String, uri: String): Boolean = {
		var status = true
		try {
			val file = new File("uridir/" + gid + ".txt")
			if (!file.exists()) status = file createNewFile()
			if (status) {
				val fWriter = new FileWriter(file getAbsoluteFile)
				val bufferedWriter = new BufferedWriter(fWriter)
				bufferedWriter write uri
				bufferedWriter close()
			}
		} catch {
			case e: Exception =>
				LogWriter writeLog("Error creating uri-file for GID " + gid + "!", Level.ERROR)
				LogWriter writeLog(e.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
				status = false
		}
		status
	}

	/**
	 * Check if a specific port is bound.
	 *
	 * @param port port number to examine
	 * @return true if port is bound to a process; false otherwise
	 */
	def portInUse(port: Int): Boolean = {
		var status = false
		var ss: ServerSocket = null
		try {
			ss = new ServerSocket(port)
			ss setReuseAddress true
		} catch {
			case ioe: IOException =>
				status = true
		} finally {
			if (ss != null) {
				ss close()
			}
		}
		status
	}

	/**
	 * Contact a web server and request its resource.
	 *
	 * @param request a string containing the target URL
	 * @return response from the server
	 */
	def crawlServer(request: String): String = {
		val response = new StringBuilder
		try {
			val htClient = HttpClientBuilder.create().build()
			val htGet = new HttpGet(request)
			htGet addHeader ("Content-Type", "text/html; charset=UTF-8")
			htGet addHeader("User-Agent", "FluxoAgent/0.1")
			val htResponse = htClient execute htGet
			val br = new BufferedReader(new InputStreamReader(htResponse.getEntity.getContent))
			var line = br.readLine
			while (line != null) {
				response append line
				line = br.readLine
			}
			br close()
			htClient close()
		} catch {
			case mue: MalformedURLException =>
				LogWriter writeLog("URL " + (request toString()) + " is malformed", Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString mue, Level.ERROR)
			case ioe: IOException =>
				LogWriter writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ioe, Level.ERROR)
			case e: Exception =>
				LogWriter writeLog("CrawlServer Exception: " + e.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
		}
		response toString()
	}

	/**
	 * Contact a web server and request its resouce (a file).
	 *
	 * @param request URL of resource
	 * @param savePath where to save the resource on the local system
	 */
	def crawlServerObject(request: String, savePath: String, gzipped: Boolean) {
		try {
			val htClient = HttpClientBuilder.create().build()
			val htGet = new HttpGet(request)
			htGet addHeader("Content-Type", "application/x-bittorrent")
			htGet addHeader("User-Agent", "FluxoAgent/0.1")
			if (gzipped) htGet addHeader("Content-Encoding", "gzip")
			val htResponse = htClient execute htGet
			val htEntity = htResponse.getEntity
			if (htEntity != null) {
				val is = htEntity.getContent
				val bufferedInputStream = new BufferedInputStream(is)
				val bufferedOutputStream = {
					if (gzipped) new BufferedOutputStream(new FileOutputStream(new File(savePath + ".gz")))
					else new BufferedOutputStream(new FileOutputStream(new File(savePath)))
				}
				var inByte: Int = bufferedInputStream.read
				while (inByte != -1) {
					bufferedOutputStream write inByte
					inByte = bufferedInputStream.read
				}
				bufferedInputStream close()
				bufferedOutputStream close()
				is close()
			}
			htClient close()
			// if this is a gzipped file, we need to extract it
			if (gzipped) {
				gunzip(savePath + ".gz", savePath)
			}
		} catch {
			case ioe: IOException =>
				LogWriter writeLog("CrawlServerObject Exception: " + ioe.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ioe, Level.ERROR)
		}
	}

	/**
	 * Convert a JSON response into <code>net.fluxo.dd.dbo.MovieObject</code>.
	 *
	 * @param raw JSON string response
	 * @return a <code>net.fluxo.dd.dbo.MovieObject</code> object
	 */
	def stringToMovieObject(raw: String):MovieObject = {
		val movie = new MovieObject
		try {
			LogWriter writeLog("-->start stringToMovieObject", Level.DEBUG)
			val json = JSONValue.parseWithException(raw).asInstanceOf[JSONObject]
			movie.MovieID_=((json get "id").asInstanceOf[Long])
			movie.MovieUrl_=((json get "url").asInstanceOf[String])
			movie.MovieTitleLong_=((json get "title_long").asInstanceOf[String])
			movie.MovieTitle_=((json get "title").asInstanceOf[String])
			movie.MovieYear_=((json get "year").asInstanceOf[Long])

			LogWriter writeLog("--> get to here", Level.DEBUG)

			movie.MovieRating_=((json get "rating").asInstanceOf[Double])
			LogWriter writeLog("--> get to here 2", Level.DEBUG)
			movie.MpaRating_=((json get "mpa_rating").asInstanceOf[String])
			LogWriter writeLog("--> get to here 3", Level.DEBUG)
			movie.Language_=((json get "language").asInstanceOf[String])
			LogWriter writeLog("--> get to here 4", Level.DEBUG)
			movie.MovieRutime_=((json get "runtime").asInstanceOf[Int])
			LogWriter writeLog("--> get to here 5", Level.DEBUG)
			movie.DateUploaded_=((json get "date_uploaded").asInstanceOf[String])

			LogWriter writeLog("--> get to here 6", Level.DEBUG)

			movie.DateUploadedEpoch_=((json get "date_uploaded_unix").asInstanceOf[Long])
			movie.CoverImage_=((json get "medium_cover_image").asInstanceOf[String])
			movie.ImdbCode_=((json get "imdb_code").asInstanceOf[String])

			LogWriter writeLog("-->middle 1", Level.DEBUG)

			val jTorrentObjects = (json get "torrents").asInstanceOf[JSONArray]
			LogWriter writeLog("-->middle 2", Level.DEBUG)
			val jTorrentIterator = jTorrentObjects.iterator
			breakable {
				while (jTorrentIterator.hasNext) {
					val jTorrentObject = jTorrentIterator.next.asInstanceOf[JSONObject]
					LogWriter writeLog("--> jTorrent iterator", Level.DEBUG)
					if ((jTorrentObject get "quality") equals "720p") {
						LogWriter writeLog("--> 720p 1", Level.DEBUG)
						movie.TorrentUrl720p_=((jTorrentObject get "url").asInstanceOf[String])
						LogWriter writeLog("--> 720p 2", Level.DEBUG)
						movie.TorrentHash720p_=((jTorrentObject get "hash").asInstanceOf[String])
						LogWriter writeLog("--> 720p 3", Level.DEBUG)
						movie.Downloaded720p_=((jTorrentObject get "download_count").asInstanceOf[Long])
						LogWriter writeLog("--> 720p 4", Level.DEBUG)
						movie.Resolution720p_=((jTorrentObject get "resolution").asInstanceOf[String])
						LogWriter writeLog("--> 720p 5", Level.DEBUG)
						movie.FrameRate720p_=((jTorrentObject get "framerate").asInstanceOf[Double])
						LogWriter writeLog("--> 720p 6", Level.DEBUG)
						movie.Quality720p_=((jTorrentObject get "quality").asInstanceOf[String])
						LogWriter writeLog("--> 720p 7", Level.DEBUG)
						movie.TorrentSeeds720p_=((jTorrentObject get "seeds").asInstanceOf[Int])
						LogWriter writeLog("--> 720p 8", Level.DEBUG)
						movie.TorrentPeers720p_=((jTorrentObject get "peers").asInstanceOf[Int])
						LogWriter writeLog("--> 720p 9", Level.DEBUG)
						movie.Size720p_=((jTorrentObject get "size").asInstanceOf[String])
						LogWriter writeLog("--> 720p 10", Level.DEBUG)
						movie.SizeByte720p_=((jTorrentObject get "size_bytes").asInstanceOf[Long])
						LogWriter writeLog("--> 720p 11", Level.DEBUG)
						movie.DateUploaded720p_=((jTorrentObject get "date_uploaded").asInstanceOf[String])
						LogWriter writeLog("--> 720p 12", Level.DEBUG)
						movie.DateUploadedEpoch720p_=((jTorrentObject get "date_uploaded_unix").asInstanceOf[Long])
						LogWriter writeLog("--> 720p 13", Level.DEBUG)
						LogWriter writeLog("--> get to here: 720p", Level.DEBUG)
					} else if ((jTorrentObject get "quality") equals "1080p") {
						LogWriter writeLog("--> 1080p 1", Level.DEBUG)
						movie.TorrentUrl1080p_=((jTorrentObject get "url").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 2", Level.DEBUG)
						movie.TorrentHash1080p_=((jTorrentObject get "hash").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 3", Level.DEBUG)
						movie.Downloaded1080p_=((jTorrentObject get "download_count").asInstanceOf[Long])
						LogWriter writeLog("--> 1080p 4", Level.DEBUG)
						movie.Resolution1080p_=((jTorrentObject get "resolution").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 5", Level.DEBUG)
						movie.FrameRate1080p_=((jTorrentObject get "framerate").asInstanceOf[Double])
						LogWriter writeLog("--> 1080p 6", Level.DEBUG)
						movie.Quality1080p_=((jTorrentObject get "quality").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 7", Level.DEBUG)
						movie.TorrentSeeds1080p_=((jTorrentObject get "seeds").asInstanceOf[Int])
						LogWriter writeLog("--> 1080p 8", Level.DEBUG)
						movie.TorrentPeers1080p_=((jTorrentObject get "peers").asInstanceOf[Int])
						LogWriter writeLog("--> 1080p 9", Level.DEBUG)
						movie.Size1080p_=((jTorrentObject get "size").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 10", Level.DEBUG)
						movie.SizeByte1080p_=((jTorrentObject get "size_bytes").asInstanceOf[Long])
						LogWriter writeLog("--> 1080p 11", Level.DEBUG)
						movie.DateUploaded1080p_=((jTorrentObject get "date_uploaded").asInstanceOf[String])
						LogWriter writeLog("--> 1080p 12", Level.DEBUG)
						movie.DateUploadedEpoch1080p_=((jTorrentObject get "date_uploaded_unix").asInstanceOf[Long])
						LogWriter writeLog("--> 1080p 13", Level.DEBUG)
						LogWriter writeLog("--> get to here: 1080p", Level.DEBUG)
					} else if ((jTorrentObject get "quality") equals "3D") {
						LogWriter writeLog("--> 3D 1", Level.DEBUG)
						movie.TorrentUrl3D_=((jTorrentObject get "url").asInstanceOf[String])
						LogWriter writeLog("--> 3D 2", Level.DEBUG)
						movie.TorrentHash3D_=((jTorrentObject get "hash").asInstanceOf[String])
						LogWriter writeLog("--> 3D 3", Level.DEBUG)
						movie.Downloaded3D_=((jTorrentObject get "download_count").asInstanceOf[Long])
						LogWriter writeLog("--> 3D 4", Level.DEBUG)
						movie.Resolution3D_=((jTorrentObject get "resolution").asInstanceOf[String])
						LogWriter writeLog("--> 3D 5", Level.DEBUG)
						movie.FrameRate3D_=((jTorrentObject get "framerate").asInstanceOf[Double])
						LogWriter writeLog("--> 3D 6", Level.DEBUG)
						movie.Quality3D_=((jTorrentObject get "quality").asInstanceOf[String])
						LogWriter writeLog("--> 3D 7", Level.DEBUG)
						movie.TorrentSeeds3D_=((jTorrentObject get "seeds").asInstanceOf[Int])
						LogWriter writeLog("--> 3D 8", Level.DEBUG)
						movie.TorrentPeers3D_=((jTorrentObject get "peers").asInstanceOf[Int])
						LogWriter writeLog("--> 3D 9", Level.DEBUG)
						movie.Size3D_=((jTorrentObject get "size").asInstanceOf[String])
						LogWriter writeLog("--> 3D 10", Level.DEBUG)
						movie.SizeByte3D_=((jTorrentObject get "size_bytes").asInstanceOf[Long])
						LogWriter writeLog("--> 3D 11", Level.DEBUG)
						movie.DateUploaded3D_=((jTorrentObject get "date_uploaded").asInstanceOf[String])
						LogWriter writeLog("--> 3D 12", Level.DEBUG)
						movie.DateUploadedEpoch3D_=((jTorrentObject get "date_uploaded_unix").asInstanceOf[Long])
						LogWriter writeLog("--> 3D 13", Level.DEBUG)
						LogWriter writeLog("--> get to here: 3D", Level.DEBUG)
					}
				}
			}

			val genres = (json get "genres").asInstanceOf[JSONArray]
			val genre: String = {
				val sb = new StringBuilder
				while (genres.iterator.hasNext) {
					if (sb.length > 0) sb append " | "
					sb append genres.iterator.next
				}
				sb toString()
			}
			LogWriter writeLog("--> get to here: genre", Level.DEBUG)
			movie.Genre_=(genre)
			LogWriter writeLog("--> get to here: after genre", Level.DEBUG)
		}
		LogWriter writeLog ("-->end stringToMovieObject", Level.DEBUG)
		movie
	}

	/**
	 * Convert <code>YIFYSearchResult</code> object to JSON string.
	 *
	 * @param obj a <code>net.fluxo.dd.dbo.YIFYSearchResult</code> object
	 * @return JSON string
	 */
	def YIFYSearchResultToJSON(obj: YIFYSearchResult): String = {
		val json = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
		json put("SearchResult", "YIFY")
		json put("status", "ok")
		json put("status_message", "Query was successful")

		val jsData = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
		jsData put("movie_count", obj.MovieCount)

		val jsArray = (new JSONArray).asInstanceOf[util.List[util.HashMap[String, Any]]]
		val movieIterator = obj.MovieList.orNull iterator()
		while (movieIterator.hasNext) {
			val x = movieIterator next()
			val movieObject = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
			movieObject put("id", (x MovieID).toString)
			movieObject put("state", (x State) getOrElse "")
			movieObject put("url", (x MovieUrl) getOrElse "")
			movieObject put("title_long", (x MovieTitleLong) getOrElse "")
			movieObject put("title", (x MovieTitle) getOrElse "")
			movieObject put("year", (x MovieYear).toString)
			movieObject put("rating", (x MovieRating).toString)
			movieObject put("mpa_rating", (x MpaRating) getOrElse "")
			movieObject put("language", (x Language) getOrElse "")
			movieObject put("runtime", (x MovieRuntime).toString)
			movieObject put("date_uploaded", (x DateUploaded) getOrElse "")
			movieObject put("date_uploaded_unix", (x DateUploadedEpoch).toString)
			movieObject put("medium_cover_image", (x CoverImage) getOrElse "")
			movieObject put("download_count", x DownloadCount)
			movieObject put("imdb_code", (x ImdbCode) getOrElse "")
			val movieGenres = (new JSONArray).asInstanceOf[util.List[String]]
			if (((x Genre) getOrElse "").length > 0) {
				val gens = ((x Genre) getOrElse "") split "|"
				for (g <- gens) {
					movieGenres.append(g)
				}
			}
			movieObject put("genres", movieGenres)
			val torrents = (new JSONArray).asInstanceOf[util.List[util.HashMap[String, Any]]]
			if ((x.TorrentUrl720p getOrElse "").length > 0) {
				val tObject = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
				tObject put("url", x.TorrentUrl720p getOrElse "")
				tObject put("hash", x.TorrentHash720p getOrElse "")
				tObject put("quality", x.Quality720p getOrElse "")
				tObject put("seeds", x.TorrentSeeds720p)
				tObject put("peers", x.TorrentPeers720p)
				tObject put("size", x.Size720p getOrElse "")
				tObject put("size_bytes", x.SizeByte720p)
				tObject put("download_count", x.Downloaded720p)
				tObject put("resolution", x.Resolution720p getOrElse "")
				tObject put("framerate", x.FrameRate720p)
				tObject put("date_uploaded", x.DateUploaded720p getOrElse "")
				tObject put("date_uploaded_unix", x.DateUploadedEpoch720p)
				torrents add tObject
			}
			if ((x.TorrentUrl1080p getOrElse "").length > 0) {
				val tObject = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
				tObject put("url", x.TorrentUrl1080p getOrElse "")
				tObject put("hash", x.TorrentHash1080p getOrElse "")
				tObject put("quality", x.Quality1080p getOrElse "")
				tObject put("seeds", x.TorrentSeeds1080p)
				tObject put("peers", x.TorrentPeers1080p)
				tObject put("size", x.Size1080p getOrElse "")
				tObject put("size_bytes", x.SizeByte1080p)
				tObject put("download_count", x.Downloaded1080p)
				tObject put("resolution", x.Resolution1080p getOrElse "")
				tObject put("framerate", x.FrameRate1080p)
				tObject put("date_uploaded", x.DateUploaded1080p getOrElse "")
				tObject put("date_uploaded_unix", x.DateUploadedEpoch1080p)
				torrents add tObject
			}
			if ((x.TorrentUrl3D getOrElse "").length > 0) {
				val tObject = (new JSONObject).asInstanceOf[util.HashMap[String, Any]]
				tObject put("url", x.TorrentUrl3D getOrElse "")
				tObject put("hash", x.TorrentHash3D getOrElse "")
				tObject put("quality", x.Quality3D getOrElse "")
				tObject put("seeds", x.TorrentSeeds3D)
				tObject put("peers", x.TorrentPeers3D)
				tObject put("size", x.Size3D getOrElse "")
				tObject put("size_bytes", x.SizeByte3D)
				tObject put("download_count", x.Downloaded3D)
				tObject put("resolution", x.Resolution3D getOrElse "")
				tObject put("framerate", x.FrameRate3D)
				tObject put("date_uploaded", x.DateUploaded3D getOrElse "")
				tObject put("date_uploaded_unix", x.DateUploadedEpoch3D)
				torrents add tObject
			}
			movieObject put("torrents", torrents)

			jsArray.add(movieObject)
		}

		jsData put ("movies", jsArray)
		// put "data" into json
		json put("data", jsData)
		val result = json.toString
		result
	}

	/**
	 * Read a file into a JSON object and return a value identified by the key.
	 *
	 * @param infoFile a file containing a JSON object
	 * @param key the key whose value we are trying to extract
	 * @return the value of associated key or empty string
	 */
	def extractValueFromJSONFile(infoFile: File, key: String): String = {
		var retVal = ""
		// open the info file and read the content into a string
		try {
			val content = FileUtils readFileToString(infoFile, "UTF-8")
			val jsParser = new JSONParser
			val jsObj = (jsParser parse content).asInstanceOf[JSONObject]
			if (jsObj containsKey key) retVal = jsObj.get(key).toString
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Failed to extract value from file " + (infoFile getName) + "; key: " + key, Level.ERROR)
		}
		retVal
	}

	/**
	 * Read a file into a JSON object and return a JSONArray that is contained within the JSON object.
	 *
	 * @param infoFile a file containing a JSON object
	 * @param arrayName array name inside the JSON object
	 * @return a <code>JSONArray</code> object
	 */
	def extractArrayFromJSONObject(infoFile: File, arrayName: String): JSONArray = {
		var retVal: Option[JSONArray] = None
		try {
			val content = FileUtils readFileToString(infoFile, "UTF-8")
			val jsParser = new JSONParser
			val jsObj = (jsParser parse content).asInstanceOf[JSONObject]
			retVal = Some(jsObj.get(arrayName).asInstanceOf[JSONArray])
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Failed to extract array from file " + (infoFile getName) + "; array name: " + arrayName, Level.ERROR)
		}
		retVal.orNull
	}

	/**
	 * Return the download progress as a JSON string.
	 *
	 * @param obj a <code>java.util.HashMap</code> object containing the download progress
	 * @return a JSON string
	 */
	def DownloadProgressToJson(obj: util.HashMap[String,String]): String = {
		val json = (new JSONObject).asInstanceOf[util.HashMap[String, Object]]
		json put("Object", "DownloadProgress")
		val iterator = obj entrySet() iterator()
		val jsArray = (new JSONArray).asInstanceOf[util.List[util.HashMap[String,String]]]
		while (iterator.hasNext) {
			val o = iterator.next
			val progressObj = (new JSONObject).asInstanceOf[util.HashMap[String,String]]
			progressObj put (o.getKey, o.getValue)
			jsArray add progressObj
		}
		json put ("Progress", jsArray)
		json.toString
	}

	/**
	 * Returns a value <code>Object</code> out of a <code>HashMap</code>.
	 *
	 * @param map a <code>java.util.HashMap</code> object
	 * @param key a <code>java.utils.String</code> key to the Map
	 * @return an Object
	 */
	def extractValueFromHashMap(map: java.util.HashMap[String, Object], key:String): Object = {
		var ret: Object = new Object
		val it = map.entrySet().iterator()
		while (it.hasNext) {
			val entry = it next()
			if (entry.getKey.equals(key)) ret = entry.getValue
		}
		ret
	}

	/**
	 * Create a new <code>XmlRpcClient</code> instance querying a specific port and returns it.
	 *
	 * @param port port number where an aria2 process is bound to
	 * @return a <code>org.apache.xmlrpc.client.XmlRpcClient</code> object
	 */
	def getXmlRpcClient(port: Int): XmlRpcClient = {
		val url = "http://127.0.0.1:" + port + "/rpc"
		val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
		xmlClientConfig setServerURL new URL(url)
		val client = new XmlRpcClient()
		client setConfig xmlClientConfig
		client setTypeFactory new XmlRpcTypeFactory(client)
		client
	}

	/**
	 * Return a unique 16-character unique ID.
	 *
	 * @return a 16-character String
	 */
	def generateGID(): String = {
		//[0-9A-F]
		val randomValue = _randomizer.nextLong()
		val sb = new StringBuilder(java.lang.Long.toHexString(randomValue))
		while (sb.length < 16) {
			sb insert(0, "0")
		}
		sb toString()
	}

	/**
	 * Return a SHA-256 hashed string.
	 *
	 * @param value original string value
	 * @return a hashed string value
	 */
	def hashString(value: String): String = {
		var retVal = value
		try {
			val md = MessageDigest getInstance "SHA-256"
			md update value.getBytes
			val digestedBytes = md digest()
			val sb = new StringBuilder
			for (b <- digestedBytes) {
				val strHex = Integer toHexString (0xff & b)
				if (strHex.length == 1) sb append '0'
				sb append strHex
			}
			retVal = sb toString()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Failed to hash string: " + ex.getMessage, Level.ERROR)
		}
		retVal
	}

	/**
	 * Send a query status message to the specified <code>XmlRpcClient</code>.
	 *
	 * @param gid the unique download ID to query
	 * @param client a <code>org.apache.xmlrpc.client.XmlRpcClient</code> object
	 * @throws org.apache.xmlrpc.XmlRpcException XmlRpcException
	 * @return an XML object representing the download status
	 */
	@throws(classOf[XmlRpcException])
	def sendAriaTellStatus(gid: String, client: XmlRpcClient): Object = {
		//val params = Array[Object](gid)
		val params = new util.ArrayList[Object]()
		params add gid
		client execute("aria2.tellStatus", params)
	}

	/**
	 * Send a query to ask for active processes to the specified <code>XmlRpcClient</code>.
	 *
	 * @param client a <code>org.apache.xmlrpc.client.XmlRpcClient</code> object
	 * @throws org.apache.xmlrpc.XmlRpcException XmlRpcException
	 * @return an array of Objects representing the active downloads
	 */
	@throws(classOf[XmlRpcException])
	def sendAriaTellActive(client: XmlRpcClient): Array[Object] = {
		val params = Array[Object]()
		val returned = client.execute("aria2.tellActive", params)
		// Returned XML-RPC is an Array Java HashMap...
		returned.asInstanceOf[Array[Object]]
	}

	/**
	 * Send a query to ask for finished processes to the specified <code>XmlRpcClient</code>.
	 *
	 * @param client a <code>org.apache.xmlrpc.client.XmlRpcClient</code> object
	 * @throws org.apache.xmlrpc.XmlRpcException XmlRpcException
	 * @return an array of Objects representing the finished downloads
	 */
	@throws(classOf[XmlRpcException])
	def sendAriaTellStopped(client: XmlRpcClient): Array[Object] = {
		val params = new util.ArrayList[Int]()
		params.add(0)
		params.add(100)
		val returned = client.execute("aria2.tellStopped", params)
		returned.asInstanceOf[Array[Object]]
	}

	/**
	 * Send the command to shutdown an aria2 process connected to the specified <code>XmlRpcClient</code>.
	 *
	 * @param client a <code>org.apache.xmlrpc.client.XmlRpcClient</code> object
	 */
	def sendAriaTellShutdown(client: XmlRpcClient) {
		client.execute("aria2.shutdown", Array[Object]())
	}

	/**
	 * Uncompress a gzipped file.
	 *
	 * @param inputFile A Gzipped file
	 * @param outputFile An uncompressed file
	 */
	def gunzip(inputFile: String, outputFile: String) {
		val buffer = new Array[Byte](1024)
		try {
			val gzi = new GZIPInputStream(new FileInputStream(inputFile))
			val gzo = new FileOutputStream(outputFile)
			var length = gzi read buffer
			while (length > 0) {
				gzo write(buffer, 0, length)
				length = gzi read buffer
			}
			gzi.close()
			gzo.close()
		} catch {
			case ioe: IOException =>
				LogWriter writeLog("Error unzipping file " + inputFile + ": " + ioe.getMessage, Level.DEBUG)
				LogWriter stackTraceToString ioe
		}
	}

	/**
	 * This class defines the behaviour of the <code>StringSerializer</code> used in the
	 * <code>XmlRpcTypeFactory</code>.
	 */
	class XmlRpcStringSerializer extends StringSerializer {
		@throws(classOf[SAXException])
		override def write(pHandler: ContentHandler, pObject: Object) {
			write(pHandler, StringSerializer.STRING_TAG, pObject.toString)
		}
	}

	/**
	 * This class defines a <code>TypeFactoryImpl</code> for our <code>XmlRpcClient</code>.
	 *
	 * @param pController an instance of <code>XmlRpcController</code>
	 */
	class XmlRpcTypeFactory(pController: XmlRpcController) extends TypeFactoryImpl(pController) {
		/**
		 * Return a <code>TypeSerializer</code> object. For our purpose here, this method will return a <code>XmlRpcStringSerializer</code>
		 * object when the pObject is a String.
		 *
		 * @param pConfig a <code>org.apache.xmlrpc.common.XmlRpcStreamConfig</code> object
		 * @param pObject a Java Object
		 * @throws org.xml.sax.SAXException SAXException
		 * @return a <code>org.apache.xmlrpc.serializer.TypeSerializer</code> object
		 */
		@throws(classOf[SAXException])
		override def getSerializer(pConfig: XmlRpcStreamConfig, pObject: Object): TypeSerializer = {
			val response: TypeSerializer = pObject match {
				case s:String => new XmlRpcStringSerializer()
				case _ => super.getSerializer(pConfig, pObject)
			}
			response
		}
	}
}

/**
 * A Singleton object for Utils.
 */
object OUtils extends Utils
