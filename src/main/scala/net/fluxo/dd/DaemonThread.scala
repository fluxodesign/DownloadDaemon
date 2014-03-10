package net.fluxo.dd

import org.apache.log4j.Level
import java.io._
import java.net.{URL, ServerSocket}
import net.fluxo.dd.dbo.{Task, Config}
import java.util.Properties
import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, XmlRpcController, TypeFactoryImpl}
import org.apache.xmlrpc.serializer.{StringSerializer, TypeSerializer}
import org.xml.sax.{SAXException, ContentHandler}
import org.joda.time.DateTime
import java.util

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
	private var _tXMPPMonitor: Option[XMPPMonitor] = None
	private var _threadXMPPMonitor: Thread = null
	private val _config = readConfiguration

	override def run() {
		_isRunning = true
		dbMan.setup()
		LogWriter.writeLog("Daemon started on " + LogWriter.currentDateTime, Level.INFO)
		if (_config.isEmpty) {
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
			LogWriter.writeLog("RPC Port " +  _config.RPCPort + " is in use; assumed aria2 has been started", Level.INFO)
			_isRunning = false
			return
		} else {
			activateAria2()
		}
		if (_isRunning) {
			val dlMon: DownloadMonitor = new DownloadMonitor(dbMan, this)
			_tDlMonitor = Some(dlMon)
			_threadDlMonitor = new Thread(_tDlMonitor.getOrElse(null))
			_threadDlMonitor.start()
			val xmppMon: XMPPMonitor = {
				if (_config.XMPPProvider.getOrElse(null).toLowerCase.equals("google")) {
					new XMPPMonitor("google", "talk.google.com", 5222, _config.XMPPAccount.getOrElse(null), _config.XMPPPassword.getOrElse(null), this)
				} else if (_config.XMPPProvider.getOrElse(null).toLowerCase.equals("facebook")) {
					new XMPPMonitor("facebook", "chat.facebook.com", 5222, _config.XMPPAccount.getOrElse(null), _config.XMPPPassword.getOrElse(null), this)
				} else null
			}
			_tXMPPMonitor = Some(xmppMon)
			_threadXMPPMonitor = new Thread(_tXMPPMonitor.getOrElse(null))
			_threadXMPPMonitor.start()
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
		new Thread(new AriaThread).start()
	}

	def startXmlRpcClient() {
		if (_aria2Process != null) {
			val url: String = "http://127.0.0.1:" + _config.RPCPort + "/rpc"
			val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
			xmlClientConfig.setServerURL(new URL(url))
			LogWriter.writeLog("Starting XML-RPC client...", Level.INFO)
			_xmlRpcClient = new XmlRpcClient()
			_xmlRpcClient.setConfig(xmlClientConfig)
			_xmlRpcClient.setTypeFactory(new XmlRpcTypeFactory(_xmlRpcClient))
		}
	}

	def sendAriaTellStatus(gid: String): Object = {
		if (_xmlRpcClient == null) {
			startXmlRpcClient()
		}
		val params = Array[Object](gid)
		_xmlRpcClient.execute("aria2.tellStatus", params)
	}

	def sendAriaTellActive(): Array[Object] = {
		if (_xmlRpcClient == null) {
			startXmlRpcClient()
		}
		val params = Array[Object]()
		val retObject = _xmlRpcClient.execute("aria2.tellActive", params)
		// Returned XML-RPC is an Array Java HashMap...
		retObject.asInstanceOf[Array[Object]]
	}

	def sendAriaTellStopped(): Array[Object] = {
		if (_xmlRpcClient == null) {
			startXmlRpcClient()
		}
		val params = new util.ArrayList[Int]()
		params.add(0)
		params.add(100)
		val retObject = _xmlRpcClient.execute("aria2.tellStopped", params)
		retObject.asInstanceOf[Array[Object]]
	}

	def sendAriaUri(uri: String, owner: String, t: Task): String = {
		var downloadGID: String = "ERR ADD_TASK FAILED"
		if (_xmlRpcClient == null) {
			startXmlRpcClient()
		}
		val params = Array[Object](Array[String](uri))
		downloadGID = _xmlRpcClient.execute("aria2.addUri", params).asInstanceOf[String]
		if (t == null) {
			val newTask: Task = new Task {
				TaskGID_=(downloadGID)
				TaskInput_=(uri)
				TaskStarted_=(DateTime.now.getMillis)
				TaskEnded_=(DateTime.now.minusYears(10).getMillis)
				TaskOwner_=(owner)
			}
			dbMan.addTask(newTask)
		} else {
			dbMan.replaceGID(t.TaskGID.getOrElse(null), downloadGID, t.TaskOwner.getOrElse(null))
		}
		downloadGID
	}

	def getUserDownloadsStatus(owner: String): Array[Task] = {
		dbMan.queryTasks(owner)
	}

	def restartAriaDownloads() {
		val activeTasks = dbMan.queryUnfinishedTasks()
		for (t <- activeTasks) {
			LogWriter.writeLog("Resuming download for " + t.TaskGID.getOrElse(null), Level.INFO)
			sendAriaUri(t.TaskInput.getOrElse(null), t.TaskOwner.getOrElse(null), t)
		}
	}

	def tryStop() {
		// Destroy aria2 process..
		if (_aria2Process != null) {
			_aria2Process.destroy()
		}
		/// TODO
		if (_tDlMonitor.isDefined) {
			_tDlMonitor.getOrElse(null).stop()
			_threadDlMonitor.interrupt()
		}
		if (_tXMPPMonitor.isDefined) {
			_tXMPPMonitor.getOrElse(null).stop()
			_threadXMPPMonitor.interrupt()
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

	class AriaThread extends Runnable {
		override def run() {
			_aria2Process = new ProcessBuilder("aria2c", "--enable-rpc", "--rpc-listen-port=" + _config.RPCPort,
				"--seed-time=0", "--max-overall-upload-limit=1", "--follow-torrent=mem",
				"--seed-ratio=0.1", "--rpc-listen-all=false").start()
			_aria2Process.waitFor()
		}
	}
}
