package net.fluxo.dd

import net.fluxo.dd.dbo.{YIFYSearchResult, MovieObject, Config}
import java.util.{Random, Properties}
import java.io._
import org.apache.log4j.Level
import java.net.{MalformedURLException, URL, ServerSocket}
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
import java.util.concurrent.TimeUnit

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 20:58 PM
 */
class Utils {

	private var _config: Option[Config] = None
	private val _randomizer: Random = new Random(System.currentTimeMillis())
	private var _externalIP: Option[String] = Some("127.0.0.1")

	def ExternalIP: String = { _externalIP.getOrElse("127.0.0.1") }
	def ExternalIP_:(value: String) { _externalIP = Some(value) }

	def readConfig: Config = {
		if (_config.isEmpty) _config = Some(readConfiguration)
		_config.getOrElse(null)
	}

	private def readConfiguration: Config = {
		val prop: Properties = new Properties
		var cfg: Config = new Config
		try {
			prop.load(new FileInputStream("./dd.properties"))
			cfg.RPCPort_= (java.lang.Integer.parseInt(prop.getProperty("rpc_port")))
			cfg.RPCLimit_=(java.lang.Integer.parseInt(prop.getProperty("rpc_limit")))
			cfg.HTTPDPort_=(java.lang.Integer.parseInt(prop.getProperty("httpd_port")))
			cfg.XMPPProvider_=(prop.getProperty("xmpp_provider"))
			cfg.XMPPAccount_=(prop.getProperty("xmpp_account"))
			cfg.XMPPPassword_=(prop.getProperty("xmpp_password"))
			cfg.DownloadDir_=(prop.getProperty("download_dir"))
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error reading properties file dd.properties", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
		cfg
	}

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

	def portInUse(port: Int): Boolean = {
		var status = false
		var ss: ServerSocket = null
		try {
			ss = new ServerSocket(port)
			ss.setReuseAddress(true)
		} catch {
			case ioe: IOException =>
				status = true
		} finally {
			if (ss != null) {
				ss.close()
			}
		}
		status
	}

	def crawlServer(request: String): String = {
		val response = new StringBuilder
		try {
			val htClient = HttpClientBuilder.create().build()
			val htGet = new HttpGet(request)
			htGet.addHeader("User-Agent", "FluxoAgent/0.1")
			val htResponse = htClient.execute(htGet)
			val br = new BufferedReader(new InputStreamReader(htResponse.getEntity.getContent))
			var line = br.readLine()
			while (line != null) {
				response append line
				line = br readLine()
			}
			br.close()
			htClient.close()
		} catch {
			case mue: MalformedURLException =>
				LogWriter writeLog("URL " + (request toString()) + " is malformed", Level.ERROR)
				LogWriter writeLog(LogWriter.stackTraceToString(mue), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
		response toString()
	}

	def killZombie(pid: Int) {
		var finished = false
		val cmdKill: String = "kill -9 " + pid.asInstanceOf[String]
		val cmdCheckPID: String = "kill -s 0 " + pid.asInstanceOf[String]

		while (!finished) {
			val process = new ProcessBuilder(cmdKill) start()
			var processExitVal = process waitFor(5, TimeUnit.SECONDS)
			LogWriter writeLog("Killing ARIA2 task with PID " + pid + ": " + processExitVal, Level.INFO)
			// now check if pid x still available...
			val checkProcess = new ProcessBuilder(cmdCheckPID) start()
			processExitVal = checkProcess waitFor(5, TimeUnit.SECONDS)
			if (processExitVal) finished = true
		}
	}

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

	def extractValueFromHashMap(map: java.util.HashMap[String, Object], key:String): Object = {
		var ret: Object = new Object
		val it = map.entrySet().iterator()
		while (it.hasNext) {
			val entry = it.next()
			if (entry.getKey.equals(key)) ret = entry.getValue
		}
		ret
	}

	def getXmlRpcClient(port: Int): XmlRpcClient = {
		val url = "http://127.0.0.1:" + port + "/rpc"
		val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
		xmlClientConfig.setServerURL(new URL(url))
		val client = new XmlRpcClient()
		client.setConfig(xmlClientConfig)
		client.setTypeFactory(new XmlRpcTypeFactory(client))
		client
	}

	def generateGID(): String = {
		//[0-9A-F]
		val randomValue = _randomizer.nextLong()
		val sb = new StringBuilder(java.lang.Long.toHexString(randomValue))
		while (sb.length < 16) {
			sb.insert(0, "0")
		}
		sb.toString()
	}

	def sendAriaTellStatus(gid: String, client: XmlRpcClient): Object = {
		//val params = Array[Object](gid)
		val params = new util.ArrayList[Object]()
		params.add(gid)
		client.execute("aria2.tellStatus", params)
	}

	@throws(classOf[XmlRpcException])
	def sendAriaTellActive(client: XmlRpcClient): Array[Object] = {
		val params = Array[Object]()
		val returned = client.execute("aria2.tellActive", params)
		// Returned XML-RPC is an Array Java HashMap...
		returned.asInstanceOf[Array[Object]]
	}

	@throws(classOf[XmlRpcException])
	def sendAriaTellStopped(client: XmlRpcClient): Array[Object] = {
		val params = new util.ArrayList[Int]()
		params.add(0)
		params.add(100)
		val returned = client.execute("aria2.tellStopped", params)
		returned.asInstanceOf[Array[Object]]
	}

	def sendAriaTellShutdown(client: XmlRpcClient) {
		client.execute("aria2.shutdown", Array[Object]())
	}

	class XmlRpcStringSerializer extends StringSerializer {
		@throws(classOf[SAXException])
		override def write(pHandler: ContentHandler, pObject: Object) {
			write(pHandler, StringSerializer.STRING_TAG, pObject.toString)
		}
	}

	class XmlRpcTypeFactory(pController: XmlRpcController) extends TypeFactoryImpl(pController) {
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

object OUtils extends Utils
