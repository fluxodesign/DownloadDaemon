/*
 * DaemonThread.scala
 *
 * Copyright (c) 2014 Ronald Kurniawan. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.fluxo.dd

import org.apache.log4j.Level
import java.io.{InputStreamReader, BufferedReader}

/**
 * DaemonThread starts and manages instances of {@link DownloadMonitor}, {@link XMPPMonitor}, {@link YIFYCacheMonitor}
 * and {@link HttpDaemon}.
 * <p>Upon starting, DaemonThread also checks whether {@code aria2} is installed and aborts if DaemonThread cannot find it.
 *
 * @param dbMan an instance of DbManager class.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 4/03/14
 * @see java.lang.Thread
 */
class DaemonThread(dbMan: DbManager) extends Thread {

	@volatile
	private var _isRunning: Boolean = false
	private var _tDlMonitor: Option[DownloadMonitor] = None
	private var _threadDlMonitor: Thread = null
	private var _tXMPPMonitor: Option[XMPPMonitor] = None
	private var _threadXMPPMonitor: Thread = null
	private var _tYIFYCacheMonitor: Option[YIFYCacheMonitor] = None
	private var _threadYIFYCacheMonitor: Thread = null
	private var _tHttpd: Option[HttpDaemon] = None
	private var _threadHttpD: Thread = null

	/**
	 * Set up necessary flags and log entries, read the configuration and run all the manager processes.
	 */
	override def run() {
		_isRunning = true
		dbMan setup()
		LogWriter writeLog("Daemon started on " + (LogWriter currentDateTime), Level.INFO)
		if ((OUtils readConfig).isEmpty) {
			LogWriter writeLog("DownloadDaemon configuration is empty", Level.ERROR)
			_isRunning = false
			return
		}
		if (!hasAria2) {
			LogWriter writeLog("This system does not have aria2c installed. Please install aria2c before trying again", Level.ERROR)
			_isRunning = false
			return
		}
		OUtils createUriDir()
		if (_isRunning) {
			val dlMon: DownloadMonitor = new DownloadMonitor(dbMan, this)
			_tDlMonitor = Some(dlMon)
			_threadDlMonitor = new Thread(_tDlMonitor getOrElse null)
			_threadDlMonitor start()
			val xmppMon: XMPPMonitor = {
				if (OUtils.readConfig.XMPPProvider.getOrElse(null).toLowerCase.equals("google")) {
					new XMPPMonitor("google", "talk.google.com", 5222, OUtils.readConfig.XMPPAccount.getOrElse(null), OUtils.readConfig.XMPPPassword.getOrElse(null), this)
				} else if (OUtils.readConfig.XMPPProvider.getOrElse(null).toLowerCase.equals("facebook")) {
					new XMPPMonitor("facebook", "chat.facebook.com", 5222, OUtils.readConfig.XMPPAccount.getOrElse(null), OUtils.readConfig.XMPPPassword.getOrElse(null), this)
				} else null
			}
			_tXMPPMonitor = Some(xmppMon)
			_threadXMPPMonitor = new Thread(_tXMPPMonitor getOrElse null)
			_threadXMPPMonitor start()
			val httpd: HttpDaemon = new HttpDaemon((OUtils readConfig) HTTPDPort)
			_tHttpd = Some(httpd)
			_threadHttpD = new Thread(_tHttpd getOrElse null)
			_threadHttpD start()
			val ycMonitor = new YIFYCacheMonitor
			_tYIFYCacheMonitor = Some(ycMonitor)
			_threadYIFYCacheMonitor = new Thread(_tYIFYCacheMonitor getOrElse null)
			_threadYIFYCacheMonitor start()
		}
	}

	/**
	 * Check whether aria2 is installed on the system.
	 * @return true if the system has aria2 installed, false otherwise
	 */
	def hasAria2: Boolean = {
		var status = false
		try {
			val proc: Process = (Runtime getRuntime) exec "which aria2c"
			proc.waitFor
			val reader: BufferedReader = new BufferedReader(new InputStreamReader(proc getInputStream))
			if ((reader readLine()) != null) {
				status = true
			}
		} catch {
			case e: Exception =>
				LogWriter writeLog("While trying to call aria2c, got exception: ", Level.ERROR)
				LogWriter writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
		}
		status
	}

	/**
	 * Try to stop all the manager processes.
	 */
	def tryStop() {
		if (_tDlMonitor.isDefined) {
			(_tDlMonitor getOrElse null) stop()
			_threadDlMonitor interrupt()
		}
		if (_tXMPPMonitor.isDefined) {
			(_tXMPPMonitor getOrElse null) stop()
			_threadXMPPMonitor interrupt()
		}
		if (_tHttpd.isDefined) {
			(_tHttpd getOrElse null) stop()
			_threadHttpD interrupt()
		}
		if (_tYIFYCacheMonitor.isDefined) {
			(_tYIFYCacheMonitor getOrElse null) stop()
			_threadYIFYCacheMonitor interrupt()
		}
	}

}
