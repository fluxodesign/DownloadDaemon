/*
 * YIFYCache.scala
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
 * Data Object for representing one entry in the YIFY cache table.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 3/04/14
 */
class YIFYCache {

	private var _movieID: Long = 0L

	def MovieID: Long = _movieID
	def MovieID_:(value: Long) { _movieID = value }

	private var _title: Option[String] = None

	def MovieTitle: Option[String] = _title
	def MovieTitle_:(value: String) { _title = Some(value) }

	private var _year: Long = 0L

	def MovieYear: Long = _year
	def MovieYear_:(value: Long) { _year = value }

	private var _quality: Option[String] = None

	def MovieQuality: Option[String] = _quality
	def MovieQuality_:(value: String) { _quality = Some(value) }

	private var _size: Option[String] = None

	def MovieSize: Option[String] = _size
	def MovieSize_:(value: String) { _size = Some(value) }

	private var _coverImage: Option[String] = None

	def MovieCoverImage: Option[String] = _coverImage
	def MovieCoverImage_:(value: String) { _coverImage = Some(value) }
}
