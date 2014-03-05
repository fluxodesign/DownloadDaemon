package net.fluxo.dd

import org.apache.log4j.Level

/**
 * User: Ronald Kurniawan (viper)
 * Date: 3/03/14
 * Time: 7:21 PM
 *
 */
object FluxoDownloadDaemon {

	private val _dbMan = DbControl
	private val _dt = new DaemonThread(_dbMan)

	def main(args: Array[String]) {
		System.out.println("DownloadDaemon version 0.1\n")
		attachShutdownHook()
		_dt.start()
	}

	def attachShutdownHook() {
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run() {
				LogWriter.writeLog("Shutdown attempted...", Level.INFO)
				LogWriter.writeLog("Shutting down database...", Level.INFO)
				_dbMan.cleanup()
				_dt.tryStop()
			}
		})
	}
}
