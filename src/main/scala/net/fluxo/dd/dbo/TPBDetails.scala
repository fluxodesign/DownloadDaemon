/*
 * TPBDetails.scala
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
 * Data Object for representing details of a bittorent object from a certain notorious
 * torrents site.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 2/05/14
 */
class TPBDetails {

	private val _id = "TPB_DETAILS"

	private var _info: String = ""

	def Info: String = { _info  }
	def Info_:(value: String) { _info = value }

	private var _request: String = ""

	def Request: String = { _request  }
	def Request_:(value: String) { _request = value }
}
