/*
 * TPBObject.scala
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
 * Data Object representing a torrent object from a certain notorious torrent site.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 24/03/14
 */
class TPBObject {

	private var _seeders: Int = 0

	def Seeders: Int = _seeders
	def Seeders_:(value: Int) { _seeders = value }

	private var _leechers: Int = 0

	def Leechers: Int = _leechers
	def Leechers_:(value: Int) { _leechers = value }

	// 403, 200, etc.
	private var _type: Int = 0

	def Type: Int = _type
	def Type_:(value: Int) { _type = value }

	private var _title: String = ""

	def Title: String = _title
	def Title_:(value: String) { _title = value }

	private var _uploaded: String = ""

	def Uploaded: String = _uploaded
	def Uploaded_=(value: String) { _uploaded = value }

	private var _size: String = ""

	def Size: String = _size
	def Size_=(value: String) { _size = value }

	private var _uploadedBy: String = ""

	def Uploader: String = _uploadedBy
	def Uploader_=(value: String) { _uploadedBy = value }

	private var _detailsURL: String = ""

	def DetailsURL: String = _detailsURL
	def DetailsURL_=(value: String) { _detailsURL = value }

	private var _magnetURL: String = ""

	def MagnetURL: String = _magnetURL
	def MagnetURL_:(value: String) { _magnetURL = value }

	private var _torrentURL: String = ""

	def TorrentURL: String = _torrentURL
	def TorrentURL_=(value: String) { _torrentURL = value }

	private var _info: String = ""

	def Info: String = _info
	def Info_:(value: String) { _info = value }
}
