package net.fluxo.dd

import org.apache.log4j.Level
import java.io.File
import org.apache.commons.io.FileUtils

/**
 * User: Ronald Kurniawan (viper)
 * Date: 6/03/14
 * Time: 10:09 PM
 */
class DownloadMonitor(dbMan: DbManager, parent: DaemonThread) extends Runnable {

	@volatile
	private var _isRunning: Boolean = true

	override def run() {
		//check for unfinished downloads and restart them...
		parent.restartAriaDownloads()
		while (_isRunning) {
			try {
				val tasks = dbMan.queryUnfinishedTasks()
				// DEBUG
				System.out.println("unfinished tasks: " + tasks.length)
				for (t <- tasks) {
					val parentStatus = parent.sendAriaTellStatus(t.TaskGID.getOrElse(null))
					// 'parentStatus' is actually a Java HashMap...
					val jMap = parentStatus.asInstanceOf[java.util.HashMap[String, Object]]
					val tgObj = extractValueFromHashMap(jMap, "followedBy").asInstanceOf[Array[Object]]
					if (tgObj.length > 0 && tgObj(0) != null) {
						dbMan.updateTaskTailGID(t.TaskGID.getOrElse(null), tgObj(0).asInstanceOf[String])
						t.TaskTailGID_=(tgObj(0).asInstanceOf[String])
					}
				}

				val progressReport = parent.sendAriaTellActive()
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
				val finishedDownloads = parent.sendAriaTellStopped()
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
						if (parent.configDownloadDir().length > 0) {
							val packageDir = new File(qf.CPPackage.getOrElse(null))
							val destDir = new File(parent.configDownloadDir())
							if (packageDir.isDirectory && packageDir.exists() && destDir.isDirectory && destDir.exists()) {
								FileUtils.moveDirectory(packageDir, destDir)
							} else LogWriter.writeLog("directory " + destDir.getAbsolutePath + " doesn't exist!", Level.INFO)
						}
					}
				}

				Thread.interrupted()
				Thread.sleep(secondToMillis(10))
			} catch {
				case ie: InterruptedException =>
					LogWriter.writeLog(ie.getMessage, Level.ERROR)
					LogWriter.writeLog(LogWriter.stackTraceToString(ie), Level.ERROR)
					if (!_isRunning) {
						cleanup()
					}
				case e: Exception =>
					LogWriter.writeLog(e.getMessage, Level.ERROR)
					LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
					if (!_isRunning) {
						cleanup()
					}
			}
		}
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

	def cleanup() {
		LogWriter.writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	def stop() {
		LogWriter.writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false

	}

	def secondToMillis(secs: Long): Long = {
		secs * 1000
	}
}
