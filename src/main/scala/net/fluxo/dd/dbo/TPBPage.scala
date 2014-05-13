/*
 * TPBPage.scala
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

import java.util

/**
 * Data Object for containing one page of results from a certain notorious torrents site.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 27/03/14
 */
class TPBPage {

	private val SearchResult = "TPB"

	private var _totalItems: Int = 0

	def TotalItems: Int = _totalItems
	def TotalItems_:(value: Int) { _totalItems = value }

	private var _tpbItems: util.ArrayList[TPBObject] = new util.ArrayList[TPBObject]

	def TPBItems: util.ArrayList[TPBObject] = _tpbItems
	def AddTPBItems(tpbo: TPBObject) { _tpbItems add tpbo }
	def TPBItems_:(value: util.ArrayList[TPBObject]) { _tpbItems = value }
}
