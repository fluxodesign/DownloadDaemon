package net.fluxo.dd

import org.apache.log4j.Level
import java.io._
import java.net.{URL, ServerSocket}
import net.fluxo.dd.dbo.{TaskStatus, Config}
import java.util.Properties
import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, XmlRpcController, TypeFactoryImpl}
import org.apache.xmlrpc.serializer.{StringSerializer, TypeSerializer}
import org.xml.sax.{SAXException, ContentHandler}

/**
 * User: Ronald Kurniawan (viper)
 * Date: 4/03/14
 * Time: 10:08 PM
 */
class DaemonThread(dbMan: DbManager) extends Thread {

	@volatile
	private var _isRunning: Boolean = false
	private var _xmlRpcClient: XmlRpcClient = _
	private var _aria2Process: Process = _
	private var _tDlMonitor: Option[DownloadMonitor] = None
	private var _threadDlMonitor: Thread = null

	override def run() {
		_isRunning = true
		dbMan.setup()
		LogWriter.writeLog("Daemon started on " + LogWriter.currentDateTime, Level.INFO)
		val config = readConfiguration
		if (config.isEmpty) {
			LogWriter.writeLog("DownloadDaemon configuration is empty", Level.ERROR)
			_isRunning = false
			return
		}
		if (!hasAria2) {
			LogWriter.writeLog("This system does not have aria2c installed. Please install aria2c before trying again", Level.ERROR)
			_isRunning = false
			return
		}
		if (isRPCPortInUse) {
			LogWriter.writeLog("RPC Port " +  config.RPCPort + " is in use; assumed aria2 has been started", Level.INFO)
			_isRunning = false
			return
		} else {
			activateAria2()
			if (_aria2Process != null) {
				val url: String = "http://127.0.0.1:" + config.RPCPort + "/rpc"
				val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
				xmlClientConfig.setServerURL(new URL(url))
				_xmlRpcClient = new XmlRpcClient()
				_xmlRpcClient.setConfig(xmlClientConfig)
				_xmlRpcClient.setTypeFactory(new XmlRpcTypeFactory(_xmlRpcClient))
			}
		}
		if (_isRunning) {
			val dlMon: DownloadMonitor = new DownloadMonitor(dbMan)
			_tDlMonitor = Some(dlMon)
			_threadDlMonitor = new Thread(_tDlMonitor.getOrElse(null))
			_threadDlMonitor.start()
		}
	}

	def isTorrentDirExists: Boolean = {
		val dir = new File("./torrents")
		dir.exists() && dir.isDirectory && dir.canRead
	}

	def readConfiguration: Config = {
		val prop: Properties = new Properties
		var cfg: Config = new Config
		try {
			prop.load(new FileInputStream("./dd.properties"))
			cfg.RPCPort_= (java.lang.Integer.parseInt(prop.getProperty("rpc_port")))
			cfg.GoogleAccount_= (prop.getProperty("google_account"))
			cfg.GooglePassword_=(prop.getProperty("google_password"))
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

	def hasAria2: Boolean = {
		var status = false
		try {
			val proc: Process = Runtime.getRuntime.exec("which aria2c")
			proc.waitFor()
			val reader: BufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream))
			if (reader.readLine() != null) {
				status = true
			}
		} catch {
			case e: Exception =>
				LogWriter.writeLog("While trying to call aria2c, got exception: ", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
		status
	}

	def isRPCPortInUse: Boolean = {
		var status = false
		var ss: ServerSocket = null
		try {
			ss = new ServerSocket(6800)
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

	def activateAria2() {
		LogWriter.writeLog("Starting aria2c...", Level.INFO)
		_aria2Process = new ProcessBuilder("aria2c", "--enable-rpc").start()
	}

	def sendAriaForceShutdown() {
		if (_xmlRpcClient != null) {
			val result: String = _xmlRpcClient.execute("aria2.forceShutdown", Array[Object]()).asInstanceOf[String]
			// DEBUG
			System.out.println("aria2 force shutdown, result: " + result)
		}
	}

	def sendAriaTellStatus(gid: String): TaskStatus = {
		val ts: TaskStatus = null
		if (_xmlRpcClient != null) {
			_xmlRpcClient.execute("aria2.tellStatus", Array[Object](Array[String](gid)))
		}
		ts
	}

	def tryStop() {
		// force shutdown aria2
		sendAriaForceShutdown()
		/// TODO
		if (_tDlMonitor.isDefined) {
			_tDlMonitor.getOrElse(null).stop()
			_threadDlMonitor.interrupt()
		}
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
