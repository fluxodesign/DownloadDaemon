package net.fluxo.dd

import org.apache.log4j.Level
import java.io._
import java.net.{URL, ServerSocket}
import net.fluxo.dd.dbo.{TaskStatus, Config}
import java.util.Properties
import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}

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
		} else {
			activateAria2()
			if (_aria2Process != null) {
				val url: String = "http://127.0.0.1:" + config.RPCPort + "/rpc"
				val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
				xmlClientConfig.setServerURL(new URL(url))
				_xmlRpcClient = new XmlRpcClient()
				_xmlRpcClient.setConfig(xmlClientConfig)
				_xmlRpcClient.setTypeFactory(new MyTypeFactoryImpl(_xmlRpcClient))
			}
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
		val ss: ServerSocket = new ServerSocket(6800)
		try {
			ss.setReuseAddress(true)
			status = true
		} catch {
			case ioe: IOException =>
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
		val rpcConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
	}

	def sendAriaTellStatus(gid: String): TaskStatus = {
		val response: JsonRpcResponse = _jsonHttp.callAService("aria2.tellStatus", gid)
		null
	}

	def tryStop() {
		// force shutdown aria2
		sendAriaForceShutdown()
		/// TODO
	}
}
