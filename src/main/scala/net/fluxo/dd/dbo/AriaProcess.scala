/*
 * AriaProcess.scala
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
package net.fluxo.dd.dbo

import org.apache.commons.exec.Executor

/**
 * Data Object used by <code>AriaProcessor</code> to represent an active download process.
 * 
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 15/03/14
 */
class AriaProcess {

	private var _port: Int = 0

	def AriaPort: Int = _port
	def AriaPort_=(value: Int) { _port = value }

	private var _process: Option[Executor] = None

	def AriaProcess: Option[Executor] = _process
	def AriaProcess_:(value: Executor) { _process = Some(value) }

	/**
	 * Kill the aria2 process.
	 */
	def killAriaProcess() {
		_process.getOrElse(null).getWatchdog.destroyProcess()
		_process = None
	}

	private var _gid: Option[String] = None

	def AriaTaskGid: Option[String] = _gid
	def AriaTaskGid_=(value: String) { _gid = Some(value) }

	private var _isRestarting: Boolean = false

	def AriaTaskRestarting: Boolean = _isRestarting
	def AriaTaskRestarting_=(value: Boolean) { _isRestarting = value }

	private var _httpDownload: Boolean = false

	def AriaHttpDownload: Boolean = _httpDownload
	def AriaHttpDownload_=(value: Boolean) { _httpDownload = value }
}
