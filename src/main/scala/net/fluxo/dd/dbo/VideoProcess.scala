/*
 * VideoProcess.scala
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

;

import org.apache.commons.exec.Executor
import scala.None

/**
 * Data Object used by <code>VideoProcess</code> to represent an active download process.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 26/05/14.
 */
class VideoProcess {

	private var _process: Option[Executor] = None

	def VideoProcess: Option[Executor] = _process

	def VideoProcess_=(value: Executor) {
		_process = Some(value)
	}

	private var _totalLength: Long = 0L

	def VideoTotalLength: Long = _totalLength

	def VideoTotalLength_:(value: Long) {
		_totalLength = value
	}

	private var _fileExt: Option[String] = None

	def VideoExt: Option[String] = _fileExt

	def VideoExt_:(value: String) {
		_fileExt = Some(value)
	}

	private var _owner: Option[String] = None

	def Owner: Option[String] = _owner

	def Owner_:(value: String) {
		_owner = Some(value)
	}

	/**
	 * Kill the youtube-dl process.
	 */
	def killVideoProcess() {
		if (_process.isDefined) (_process getOrElse null).getWatchdog.destroyProcess()
		_process = None
	}

	private var _gid: Option[String] = None

	def VideoTaskGid: Option[String] = _gid

	def VideoTaskGid_=(value: String) {
		_gid = Some(value)
	}

	private var _isRestarting: Boolean = false

	def VideoTaskRestarting: Boolean = _isRestarting

	def VideoTaskRestarting_=(value: Boolean) {
		_isRestarting = value
	}

	private var _lastUpdated: Long = 0

	def VideoLastUpdated: Long = _lastUpdated

	def VideoLastUpdated_=(value: Long) {
		_lastUpdated = value
	}

	private var _title: Option[String] = None

	def VideoTitle: Option[String] = _title

	def VideoTitle_=(value: String) {
		_title = Some(value)
	}
}
