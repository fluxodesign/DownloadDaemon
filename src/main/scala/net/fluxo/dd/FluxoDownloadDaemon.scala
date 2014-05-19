/*
 * FluxoDownloadDaemon.scala
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

/**
 * Singleton object which is the entry point to the application.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 3/03/14
 */
object FluxoDownloadDaemon {

	private val _dbMan = DbControl
	private val _dt = new DaemonThread(_dbMan)

	/**
	 * The entry point to the application. It calls the <code>attachShutdownHook()</code> method and
	 * starts the <code>DaemonThread</code>.
	 *
	 * @param args string parameters
	 */
	def main(args: Array[String]) {
		System.out.println("DownloadDaemon version 0.4.5\n")
		attachShutdownHook()
		_dt start()
	}

	/**
	 * Add the shutdown hook into the runtime, so we can clean up the process when the application exits.
	 */
	def attachShutdownHook() {
		val t: Thread = new Thread {
			override def run() {
				LogWriter writeLog("Shutdown attempted...", Level.INFO)
				LogWriter writeLog("Shutting down database...", Level.INFO)
				_dbMan cleanup()
				_dt tryStop()
			}
		}
		t setDaemon true
		Runtime.getRuntime.addShutdownHook(t)
	}
}
