/*
 * CountPackage.scala
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
 * Data Object used by `DbManager#queryFinishTask` to represent task(s)
 * that already finished downloading and need to be marked as such.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 10/03/14
 */
class CountPackage {

	private var _count: Int = 0

	def CPCount: Int = _count
	def CPCount_= (value: Int) {
		_count = value
	}

	private var _package: Option[String] = None

	def CPPackage: Option[String] = _package
	def CPPackage_= (value: String) {
		_package = Some(value)
	}
}
