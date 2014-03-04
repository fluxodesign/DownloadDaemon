package net.fluxo.dd

import org.apache.log4j.Level
import java.io.File

/**
 * User: Ronald Kurniawan (viper)
 * Date: 4/03/14
 * Time: 10:08 PM
 */
class DaemonThread extends Thread {

	@volatile
	private var _isRunning: Boolean = false

	override def run() {
		_isRunning = true
		LogWriter.writeLog("Daemon started on " + LogWriter.currentDateTime, Level.INFO)
		if (_isRunning) {
			// we need to check the ./torrents directory to see if there is any new ones to download...
			if (isTorrentDirExists) {

			}
		}
	}

	def isTorrentDirExists: Boolean = {
		val dir = new File("./torrents")
		dir.exists() && dir.isDirectory && dir.canRead
	}
}
