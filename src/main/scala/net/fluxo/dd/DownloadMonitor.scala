package net.fluxo.dd

import org.apache.log4j.Level

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
				for (t <- tasks) {
					val parentStatus = parent.sendAriaTellStatus(t.TaskGID.getOrElse(null))
					// 'parentStatus' is actually a Java HashMap...
					val jMap = parentStatus.asInstanceOf[java.util.HashMap[String, Object]]
					val tgObj = extractValueFromHashMap(jMap, "followedBy").asInstanceOf[Array[Object]]
					if (tgObj.length > 0) {
						dbMan.updateTaskTailGID(t.TaskGID.getOrElse(null), tgObj(0).asInstanceOf[String])
						t.TaskTailGID_=(tgObj(0).asInstanceOf[String])
					}
				}

				val progressReport = parent.sendAriaTellActive()
				// 'progressReport' is an array of Objects; we need to cast EACH Object INTO a Java HashMap...
				for (o <- progressReport) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					val tailGID = extractValueFromHashMap(jMap, "gid").toString
					val task = {
						if (tailGID.length > 0) dbMan.queryTaskTailGID(tailGID) else null
					}
					task.TaskCompletedLength_=(extractValueFromHashMap(jMap, "completedLength").toString.toLong)
					task.TaskTotalLength_=(extractValueFromHashMap(jMap, "totalLength").toString.toLong)
					task.TaskStatus_=(extractValueFromHashMap(jMap, "status").toString)
					// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
					val btDetailsMap = extractValueFromHashMap(jMap, "bittorrent").asInstanceOf[java.util.HashMap[String, Object]]
					val infoMap = extractValueFromHashMap(btDetailsMap, "info").asInstanceOf[java.util.HashMap[String, Object]]
					task.TaskPackage_=(extractValueFromHashMap(infoMap, "name").toString)
					dbMan.updateTask(task)
				}

				Thread.interrupted()
				Thread.sleep(secondToMillis(10))
			} catch {
				case ie: InterruptedException =>
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
