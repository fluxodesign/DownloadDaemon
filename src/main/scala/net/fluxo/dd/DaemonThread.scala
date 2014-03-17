package net.fluxo.dd

import org.apache.log4j.Level
import java.io._

/**
 * User: Ronald Kurniawan (viper)
 * Date: 4/03/14
 * Time: 10:08 PM
 */
class DaemonThread(dbMan: DbManager) extends Thread {

	@volatile
	private var _isRunning: Boolean = false
	private var _tDlMonitor: Option[DownloadMonitor] = None
	private var _threadDlMonitor: Thread = null
	private var _tXMPPMonitor: Option[XMPPMonitor] = None
	private var _threadXMPPMonitor: Thread = null
	private var _tHttpd: Option[HttpDaemon] = None
	private var _threadHttpD: Thread = null

	override def run() {
		_isRunning = true
		dbMan.setup()
		LogWriter.writeLog("Daemon started on " + LogWriter.currentDateTime, Level.INFO)
		if (OUtils.readConfig.isEmpty) {
			LogWriter.writeLog("DownloadDaemon configuration is empty", Level.ERROR)
			_isRunning = false
			return
		}
		if (!hasAria2) {
			LogWriter.writeLog("This system does not have aria2c installed. Please install aria2c before trying again", Level.ERROR)
			_isRunning = false
			return
		}
		if (_isRunning) {
			val dlMon: DownloadMonitor = new DownloadMonitor(dbMan, this)
			_tDlMonitor = Some(dlMon)
			_threadDlMonitor = new Thread(_tDlMonitor.getOrElse(null))
			_threadDlMonitor.start()
			val xmppMon: XMPPMonitor = {
				if (OUtils.readConfig.XMPPProvider.getOrElse(null).toLowerCase.equals("google")) {
					new XMPPMonitor("google", "talk.google.com", 5222, OUtils.readConfig.XMPPAccount.getOrElse(null), OUtils.readConfig.XMPPPassword.getOrElse(null), this)
				} else if (OUtils.readConfig.XMPPProvider.getOrElse(null).toLowerCase.equals("facebook")) {
					new XMPPMonitor("facebook", "chat.facebook.com", 5222, OUtils.readConfig.XMPPAccount.getOrElse(null), OUtils.readConfig.XMPPPassword.getOrElse(null), this)
				} else null
			}
			_tXMPPMonitor = Some(xmppMon)
			_threadXMPPMonitor = new Thread(_tXMPPMonitor.getOrElse(null))
			_threadXMPPMonitor.start()
			val httpd: HttpDaemon = new HttpDaemon(OUtils.readConfig.HTTPDPort)
			_tHttpd = Some(httpd)
			_threadHttpD = new Thread(_tHttpd.getOrElse(null))
			_threadHttpD.start()
		}
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

	def tryStop() {
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

}
