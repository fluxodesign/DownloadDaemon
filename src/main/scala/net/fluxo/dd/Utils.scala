package net.fluxo.dd

import net.fluxo.dd.dbo.Config
import java.util.{Random, Properties}
import java.io.{IOException, FileInputStream}
import org.apache.log4j.Level
import java.net.{URL, ServerSocket}
import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.apache.xmlrpc.serializer.{TypeSerializer, StringSerializer}
import org.xml.sax.{ContentHandler, SAXException}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, TypeFactoryImpl, XmlRpcController}
import java.util
import org.apache.xmlrpc.XmlRpcException

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 20:58 PM
 */
class Utils {

	private var _config: Option[Config] = None
	private val _randomizer: Random = new Random(System.currentTimeMillis())

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
		LogWriter.writeLog("Starting XML-RPC client...", Level.INFO)
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

	def sendAriaTellActive(client: XmlRpcClient): Array[Object] = {
		val retObject = Array[Object]()
		try {
			val params = Array[Object]()
			val returned = client.execute("aria2.tellActive", params)
			// Returned XML-RPC is an Array Java HashMap...
			return returned.asInstanceOf[Array[Object]]
		} catch {
			case xe: XmlRpcException =>
				LogWriter.writeLog(xe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(xe), Level.ERROR)
		}
		retObject
	}

	def sendAriaTellStopped(client: XmlRpcClient): Array[Object] = {
		val retObject = Array[Object]()
		try {
			val params = new util.ArrayList[Int]()
			params.add(0)
			params.add(100)
			val returned = client.execute("aria2.tellStopped", params)
			return returned.asInstanceOf[Array[Object]]
		} catch {
			case xe: XmlRpcException =>
				LogWriter.writeLog(xe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(xe), Level.ERROR)
		}
		retObject
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
