package net.fluxo.dd

import org.quartz.{Job, JobExecutionContext, JobExecutionException}
import org.apache.log4j.Level
import java.io.File
import org.apache.commons.io.FileUtils

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
				val client = OUtils.getXmlRpcClient(a.AriaPort)

				// we need to acquire the TAIL GID if this is a new download, or a restart...
				val tasks = DbControl.queryTask(a.AriaTaskGid.getOrElse(null))
				if (tasks.length > 0 && !tasks(0).IsTaskCompleted) {
					if (tasks(0).TaskTailGID.getOrElse("").equals("0") || a.AriaTaskRestarting) {
						val ts = OUtils.sendAriaTellStatus(tasks(0).TaskGID.getOrElse(""), client)
						val jmap = ts.asInstanceOf[java.util.HashMap[String, Object]]
						if (!a.AriaHttpDownload) {
							val tg = OUtils.extractValueFromHashMap(jmap, "followedBy").asInstanceOf[Array[Object]]
							if (tg.length > 0 && tg(0) != null) {
								DbControl.updateTaskTailGID(tasks(0).TaskGID.getOrElse(""), tg(0).asInstanceOf[String])
							}
						}
					}
				}

				val activeTasks = OUtils.sendAriaTellActive(client)
				for (o <- activeTasks) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					// DEBUG
					System.out.println("ACTIVE: " + jMap)
					val tailGID = OUtils.extractValueFromHashMap(jMap, "gid").toString
					val task = {
						if (tailGID.length > 0) DbControl.queryTaskTailGID(tailGID) else null
					}
					val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
					task.TaskCompletedLength_=(cl)
					val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
					task.TaskTotalLength_=(tl)
					task.TaskStatus_=(OUtils.extractValueFromHashMap(jMap, "status").toString)
					task.TaskInfoHash_=({
						if (task.TaskIsHttp) "noinfohash"
						else OUtils.extractValueFromHashMap(jMap, "infoHash").toString
					})
					// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
					if (!a.AriaHttpDownload) {
						val btDetailsMap = OUtils.extractValueFromHashMap(jMap, "bittorrent").asInstanceOf[java.util.HashMap[String, Object]]
						val infoMap = OUtils.extractValueFromHashMap(btDetailsMap, "info").asInstanceOf[java.util.HashMap[String, Object]]
						task.TaskPackage_=(OUtils.extractValueFromHashMap(infoMap, "name").toString)
					}
					DbControl.updateTask(task)
				}

				val finishedTasks = OUtils.sendAriaTellStopped(client)
				// DEBUG
				System.out.println("finishedTasks: " + finishedTasks.length)
				for (o <- finishedTasks) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					val status = OUtils.extractValueFromHashMap(jMap, "status").toString
					val gid = OUtils.extractValueFromHashMap(jMap, "gid").toString
					// DEBUG
					System.out.println("finished GID: " + gid)
					val infoHash = {
						val tasks = DbControl.queryTask(gid)
						if (tasks.length > 0 && tasks(0).TaskIsHttp) "noinfohash"
						else OUtils.extractValueFromHashMap(jMap, "infoHash").toString
					}
					val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
					// DEBUG
					System.out.println("finished completed length: " + cl)
					val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
					// DEBUG
					System.out.println("finished total length: " + tl)
					val qf = DbControl.queryFinishTask(gid, infoHash, tl)
					if (qf.CPCount > 0) {
						DbControl.finishTask(status, cl, gid, infoHash, tl)
						flagCompleted = true
						// move the package to a directory specified in config...
						if (OUtils.readConfig.DownloadDir.getOrElse(null).length > 0) {
							if (a.AriaHttpDownload) {
								// HTTP downloads usually are just for 1 file...
								val packageFile = new File(qf.CPPackage.getOrElse(null))
								val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse(""))
								if (packageFile.isFile && packageFile.exists() && destDir.isDirectory && destDir.exists()) {
									FileUtils.moveFileToDirectory(packageFile, destDir, false)
								} else LogWriter.writeLog("Failed to move file " + qf.CPPackage.getOrElse("{empty file}") +
									" to " + OUtils.readConfig.DownloadDir.getOrElse("{empty target dir}"), Level.INFO)
							} else {
								val packageDir = new File(qf.CPPackage.getOrElse(null))
								val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse("") + "/" + qf.CPPackage.getOrElse(""))
								if (packageDir.isDirectory && packageDir.exists() && !destDir.exists()) {
									FileUtils.moveDirectory(packageDir, destDir)
								} else LogWriter.writeLog("directory " + destDir.getAbsolutePath + " doesn't exist!", Level.INFO)
							}
						}
					}
				}

				// shutdown this aria2 process when it's update is finished...
				if (activeTasks.length == 0 && flagCompleted) {
					OUtils.sendAriaTellShutdown(client)
					iterator.remove()
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
}
