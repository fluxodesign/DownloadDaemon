package net.fluxo.dd

import org.apache.xmlrpc.client.{XmlRpcClientConfigImpl, XmlRpcClient}
import org.quartz.{Job, JobExecutionContext, JobExecutionException}
import org.apache.log4j.Level
import java.io.File
import org.apache.commons.io.FileUtils
import java.net.URL
import java.util
import org.apache.xmlrpc.serializer.{TypeSerializer, StringSerializer}
import org.xml.sax.{ContentHandler, SAXException}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, TypeFactoryImpl, XmlRpcController}

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 12:57 PM
 */
class UpdateProgressJob extends Job {

	@throws(classOf[JobExecutionException])
	override def execute(context: JobExecutionContext) {
		try {
			val iterator = OAria.ActiveProcesses.iterator()
			while (iterator.hasNext) {
				var flagCompleted: Boolean = false
				// get an RPC client for a particular port...
				val a = iterator.next()
				val url = "http://127.0.0.1:" + a.AriaPort + "/rpc"
				val xmlClientConfig: XmlRpcClientConfigImpl = new XmlRpcClientConfigImpl()
				xmlClientConfig.setServerURL(new URL(url))
				LogWriter.writeLog("Starting XML-RPC client...", Level.INFO)
				val client = new XmlRpcClient()
				client.setConfig(xmlClientConfig)
				client.setTypeFactory(new XmlRpcTypeFactory(client))

				val activeTasks = sendAriaTellActive(client)
				// DEBUG
				System.out.println("Active task(s) for port " + a.AriaPort + ": " + activeTasks.length)
				for (o <- activeTasks) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					val tailGID = OUtils.extractValueFromHashMap(jMap, "gid").toString
					val task = {
						if (tailGID.length > 0) DbControl.queryTaskTailGID(tailGID) else null
					}
					val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
					task.TaskCompletedLength_=(cl)
					val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
					task.TaskTotalLength_=(tl)
					task.TaskStatus_=(OUtils.extractValueFromHashMap(jMap, "status").toString)
					// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
					val btDetailsMap = OUtils.extractValueFromHashMap(jMap, "bittorrent").asInstanceOf[java.util.HashMap[String, Object]]
					val infoMap = OUtils.extractValueFromHashMap(btDetailsMap, "info").asInstanceOf[java.util.HashMap[String, Object]]
					task.TaskPackage_=(OUtils.extractValueFromHashMap(infoMap, "name").toString)
					DbControl.updateTask(task)
				}

				val finishedTasks = sendAriaTellStopped(client)
				// DEBUG
				System.out.println("Finished task(s) for port: " + a.AriaPort + ": " + finishedTasks.length)
				for (o <- finishedTasks) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					val status = OUtils.extractValueFromHashMap(jMap, "status").toString
					val gid = OUtils.extractValueFromHashMap(jMap, "gid").toString
					val infoHash = OUtils.extractValueFromHashMap(jMap, "infoHash").toString
					val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
					val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
					val qf = DbControl.queryFinishTask(gid, infoHash, tl)
					if (qf.CPCount > 0) {
						DbControl.finishTask(status, cl, gid, infoHash, tl)
						flagCompleted = true
						// move the package to a directory specified in config...
						if (OUtils.readConfig.DownloadDir.getOrElse(null).length > 0) {
							val packageDir = new File(qf.CPPackage.getOrElse(null))
							val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse(null))
							if (packageDir.isDirectory && packageDir.exists() && destDir.isDirectory && destDir.exists()) {
								FileUtils.moveDirectory(packageDir, destDir)
							} else LogWriter.writeLog("directory " + destDir.getAbsolutePath + " doesn't exist!", Level.INFO)
						}
					}
				}

				// shutdown this aria2 process when it's update is finished...
				if (activeTasks.length == 0 && flagCompleted) sendAriaTellShutdown(client)
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

	def sendAriaTellStatus(gid: String, client: XmlRpcClient): Object = {
		//val params = Array[Object](gid)
		val params = new util.ArrayList[Object]()
		params.add(gid)
		client.execute("aria2.tellStatus", params)
	}

	def sendAriaTellActive(client: XmlRpcClient): Array[Object] = {
		val params = Array[Object]()
		val retObject = client.execute("aria2.tellActive", params)
		// Returned XML-RPC is an Array Java HashMap...
		retObject.asInstanceOf[Array[Object]]
	}

	def sendAriaTellStopped(client: XmlRpcClient): Array[Object] = {
		val params = new util.ArrayList[Int]()
		params.add(0)
		params.add(100)
		val retObject = client.execute("aria2.tellStopped", params)
		retObject.asInstanceOf[Array[Object]]
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
