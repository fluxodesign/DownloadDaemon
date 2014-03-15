package net.fluxo.dd

import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.quartz.{Job, JobExecutionContext, JobExecutionException}
import org.apache.log4j.Level
import java.io.{IOException, FileInputStream, File}
import org.apache.commons.io.FileUtils
import java.util.Properties
import java.net.{ServerSocket, URL}
import java.util
import net.fluxo.dd.dbo.Task
import org.joda.time.DateTime
import org.apache.xmlrpc.serializer.{TypeSerializer, StringSerializer}
import org.xml.sax.{ContentHandler, SAXException}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, TypeFactoryImpl, XmlRpcController}

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 12:57 PM
 */
class UpdateProgressJob extends Job {

	var dbMan: DbManager = null
	var _xmlRpcClient: XmlRpcClient = null

	def setObjects(o: DbManager) {
		dbMan = o
	}

	@throws(classOf[JobExecutionException])
	override def execute(context: JobExecutionContext) {
		try {
			val tasks = dbMan.queryUnfinishedTasks()
			// DEBUG
			System.out.println("unfinished tasks: " + tasks.length)
			for (t <- tasks) {
				LogWriter.writeLog("Querying active task status for TaskGID " + t.TaskGID.getOrElse(null), Level.INFO)
				val parentStatus = sendAriaTellStatus(t.TaskGID.getOrElse(null))
				// DEBUG
				System.out.println("got parent status!")
				// 'parentStatus' is actually a Java HashMap...
				val jMap = parentStatus.asInstanceOf[java.util.HashMap[String, Object]]
				val tgObj = extractValueFromHashMap(jMap, "followedBy").asInstanceOf[Array[Object]]
				if (tgObj.length > 0 && tgObj(0) != null) {
					dbMan.updateTaskTailGID(t.TaskGID.getOrElse(null), tgObj(0).asInstanceOf[String])
					t.TaskTailGID_=(tgObj(0).asInstanceOf[String])
				}
			}

			val progressReport = sendAriaTellActive()
			// DEBUG
			System.out.println("active downloads: " + progressReport.length)
			// 'progressReport' is an array of Objects; we need to cast EACH Object INTO a Java HashMap...
			for (o <- progressReport) {
				val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
				val tailGID = extractValueFromHashMap(jMap, "gid").toString
				val task = {
					if (tailGID.length > 0) dbMan.queryTaskTailGID(tailGID) else null
				}
				val cl = extractValueFromHashMap(jMap, "completedLength").toString.toLong
				task.TaskCompletedLength_=(cl)
				val tl = extractValueFromHashMap(jMap, "totalLength").toString.toLong
				task.TaskTotalLength_=(tl)
				task.TaskStatus_=(extractValueFromHashMap(jMap, "status").toString)
				// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
				val btDetailsMap = extractValueFromHashMap(jMap, "bittorrent").asInstanceOf[java.util.HashMap[String, Object]]
				val infoMap = extractValueFromHashMap(btDetailsMap, "info").asInstanceOf[java.util.HashMap[String, Object]]
				task.TaskPackage_=(extractValueFromHashMap(infoMap, "name").toString)
				dbMan.updateTask(task)
			}

			// if a download is over, the "aria2.tellStopped" should show it...
			val finishedDownloads = sendAriaTellStopped()
			// DEBUG
			System.out.println("finished downloads: " + finishedDownloads.length)
			for (o <- finishedDownloads) {
				val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
				val status = extractValueFromHashMap(jMap, "status").toString
				val gid = extractValueFromHashMap(jMap, "gid").toString
				val infoHash = extractValueFromHashMap(jMap, "infoHash").toString
				val cl = extractValueFromHashMap(jMap, "completedLength").toString.toLong
				val tl = extractValueFromHashMap(jMap, "totalLength").toString.toLong
				val qf = dbMan.queryFinishTask(gid, infoHash, tl)
				if (qf.CPCount > 0) {
					dbMan.finishTask(status, cl, gid, infoHash, tl)
					// move the package to a directory specified in config...
					if (readConfiguration("download_dir").length > 0) {
						val packageDir = new File(qf.CPPackage.getOrElse(null))
						val destDir = new File(readConfiguration("download_dir"))
						if (packageDir.isDirectory && packageDir.exists() && destDir.isDirectory && destDir.exists()) {
							FileUtils.moveDirectory(packageDir, destDir)
						} else LogWriter.writeLog("directory " + destDir.getAbsolutePath + " doesn't exist!", Level.INFO)
					}
				}
			}
		} catch {
			case ie: InterruptedException =>
				LogWriter.writeLog(ie.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ie), Level.ERROR)
			case e: Exception =>
				LogWriter.writeLog(e.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
	}

	def isRPCPortInUse: Boolean = {
		var status = false
		var ss: ServerSocket = null
		try {
			ss = new ServerSocket(Integer.parseInt(readConfiguration("rpc_port")))
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

	def readConfiguration(property: String): String = {
		val prop: Properties = new Properties
		var retVal: String = null
		try {
			prop.load(new FileInputStream("./dd.properties"))
			retVal = prop.getProperty(property, "")
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error reading properties file dd.properties", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
		retVal
	}

	def extractValueFromHashMap(map: java.util.HashMap[String, Object], key:String): Object = {
		var ret: Object = null
		val it = map.entrySet().iterator()
		while (it.hasNext) {
			val entry = it.next()
			if (entry.getKey.equals(key)) ret = entry.getValue
		}
		ret
	}

	def startXmlRpcClient() {
		if (isRPCPortInUse) {
			val url: String = "http://127.0.0.1:" + readConfiguration("rpc_port") + "/rpc"
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
		//val params = Array[Object](gid)
		val params = new util.ArrayList[Object]()
		params.add(gid)
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
