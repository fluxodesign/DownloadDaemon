/*
 * Task.scala
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

/**
 * Data Object for representing a Task (active or finished) on the database.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 5/03/14
 */
class Task {

	private var _gid: Option[String] = None

	def TaskGID: Option[String] = _gid
	def TaskGID_= (value: String) {
		_gid = Some(value)
	}

	private var _tailGID: Option[String] = None

	def TaskTailGID: Option[String] = _tailGID
	def TaskTailGID_= (value: String) {
		_tailGID = Some(value)
	}

	private var _input: Option[String] = None

	def TaskInput: Option[String] = _input
	def TaskInput_= (value: String) {
		_input = Some(value)
	}

	private var _started: Long = 0

	def TaskStarted: Long = _started
	def TaskStarted_= (value: Long) {
		_started = value
	}

	private var _ended: Long = 0

	def TaskEnded: Long = _ended
	def TaskEnded_= (value: Long) {
		_ended = value
	}

	private var _completed: Boolean = false

	def IsTaskCompleted: Boolean = _completed
	def IsTaskCompleted_= (value: Boolean) {
		_completed = value
	}

	private var _owner: Option[String] = None

	def TaskOwner: Option[String] = _owner
	def TaskOwner_= (value: String) {
		_owner = Some(value)
	}

	private var _package: Option[String] = None

	def TaskPackage: Option[String] = _package
	def TaskPackage_= (value: String) {
		_package = Some(value)
	}

	private var _status: Option[String] = None

	def TaskStatus: Option[String] = _status
	def TaskStatus_= (value: String) {
		_status = Some(value)
	}

	private var _totalLength: Long = 0

	def TaskTotalLength: Long = _totalLength
	def TaskTotalLength_= (value: Long) {
		_totalLength = value
	}

	private var _completedLength: Long = 0

	def TaskCompletedLength: Long = _completedLength
	def TaskCompletedLength_= (value: Long) {
		_completedLength = value
	}

	private var _infoHash: Option[String] = None

	def TaskInfoHash: Option[String] = _infoHash
	def TaskInfoHash_=(value: String) {
		_infoHash = Some(value)
	}

	private var _isHttp: Boolean = false

	def TaskIsHttp: Boolean = _isHttp
	def TaskIsHttp_=(value: Boolean) { _isHttp = value }

	private var _httpUsername: Option[String] = None

	def TaskHttpUsername: Option[String] = _httpUsername
	def TaskHttpUsername_=(value: String) { _httpUsername = Some(value) }

	private var _httpPassword: Option[String] = None

	def TaskHttpPassword: Option[String] = _httpPassword
	def TaskHttpPassword_=(value: String) { _httpPassword = Some(value) }
}
