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
					parent.sendAriaTellStatus(t.TaskGID.getOrElse(null))
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
