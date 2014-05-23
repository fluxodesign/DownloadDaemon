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

import net.fluxo.dd.dbo.{YIFYSearchResult, MovieObject, Config}
import java.util.{Random, Properties}
import java.io._
import org.apache.log4j.Level
import java.net._
import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.apache.xmlrpc.serializer.{TypeSerializer, StringSerializer}
import org.xml.sax.{ContentHandler, SAXException}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, TypeFactoryImpl, XmlRpcController}
import java.util
import org.apache.xmlrpc.XmlRpcException
import scala.util.control.Breaks._
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.client.methods.HttpGet
import org.json.simple.{JSONArray, JSONValue, JSONObject}
import scala.Some
import org.apache.commons.io.FileUtils
import java.security.MessageDigest

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
		_config.getOrElse(null)
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
	 * Convert a JSON response into <code>net.fluxo.dd.dbo.MovieObject</code>.
	 *
	 * @param raw JSON string response
	 * @return a <code>net.fluxo.dd.dbo.MovieObject</code> object
	 */
	def stringToMovieObject(raw: String):MovieObject = {
		val movie = new MovieObject
		try {
			val json = JSONValue.parseWithException(raw).asInstanceOf[JSONObject]
			movie.MovieID_=((json get "MovieID").asInstanceOf[String])
			movie.MovieUrl_=((json get "MovieUrl").asInstanceOf[String])
			movie.MovieTitleClean_=((json get "MovieTitleClean").asInstanceOf[String])
			movie.MovieYear_=((json get "MovieYear").asInstanceOf[String].toInt)
			movie.DateUploaded_=((json get "DateUploaded").asInstanceOf[String])
			movie.DateUploadedEpoch_=((json get "DateUploadedEpoch").asInstanceOf[Long])
			movie.Quality_=((json get "Quality").asInstanceOf[String])
			movie.CoverImage_=((json get "MediumCover").asInstanceOf[String])
			movie.ImdbCode_=((json get "ImdbCode").asInstanceOf[String])
			movie.ImdbLink_=((json get "ImdbLink").asInstanceOf[String])
			movie.Size_=((json get "Size").asInstanceOf[String])
			movie.SizeByte_=((json get "SizeByte").asInstanceOf[String].toLong)
			movie.MovieRating_=((json get "MovieRating").asInstanceOf[String])
			var genre = json get "Genre1"
			if ((json get "Genre2") != null && (json get "Genre2").asInstanceOf[String].length > 0) {
				if (!(json get "Genre2").equals("null")) genre += "|" + (json get "Genre2")
			}
			movie.Genre_=(genre.asInstanceOf[String])
			movie.Uploader_=((json get "Uploader").asInstanceOf[String])
			movie.Downloaded_=((json get "Downloaded").asInstanceOf[String].toInt)
			movie.TorrentSeeds_=((json get "TorrentSeeds").asInstanceOf[String].toInt)
			movie.TorrentPeers_=((json get "TorrentPeers").asInstanceOf[String].toInt)
			movie.TorrentUrl_=((json get "TorrentUrl").asInstanceOf[String])
			movie.TorrentHash_=((json get "TorrentHash").asInstanceOf[String])
			movie.TorrentMagnetUrl_=((json get "TorrentMagnetUrl").asInstanceOf[String])
		}
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
		json put("MovieCount", obj.MovieCount)
		val jsArray = (new JSONArray).asInstanceOf[util.List[util.HashMap[String, String]]]
		val movieIterator = (obj MovieList) getOrElse null iterator()
		while (movieIterator.hasNext) {
			val x = movieIterator next()
			val movieObject = (new JSONObject).asInstanceOf[util.HashMap[String, String]]
			movieObject put("MovieID", (x MovieID) getOrElse "")
			movieObject put("State", (x State) getOrElse "")
			movieObject put("MovieUrl", (x MovieUrl) getOrElse "")
			movieObject put("MovieTitleClean", (x MovieTitleClean) getOrElse "")
			movieObject put("MovieYear", (x MovieYear).toString)
			movieObject put("DateUploaded", (x DateUploaded) getOrElse "")
			movieObject put("DateUploadedEpoch", (x DateUploadedEpoch).toString)
			movieObject put("Quality", (x Quality) getOrElse "")
			movieObject put("CoverImage", (x CoverImage) getOrElse "")
			movieObject put("ImdbCode", (x ImdbCode) getOrElse "")
			movieObject put("ImdbLink", (x ImdbLink) getOrElse "")
			movieObject put("Size", (x Size) getOrElse "")
			movieObject put("SizeByte", (x SizeByte).toString)
			movieObject put("MovieRating", (x MovieRating) getOrElse "")
			movieObject put("Genre", (x Genre) getOrElse "")
			movieObject put("Uploader", (x Uploader) getOrElse "")
			movieObject put("Downloaded", (x Downloaded).toString)
			movieObject put("TorrentSeeds", (x TorrentSeeds).toString)
			movieObject put("TorrentPeers", (x TorrentPeers).toString)
			movieObject put("TorrentUrl", (x TorrentUrl) getOrElse "")
			movieObject put("TorrentHash", (x TorrentHash) getOrElse "")
			movieObject put("TorrentMagnetUrl", (x TorrentMagnetUrl) getOrElse "")
			jsArray.add(movieObject)
		}
		json put("MovieList", jsArray)
		json.toString
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
				if ((strHex length) == 1) sb append '0'
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
