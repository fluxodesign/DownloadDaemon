/*
 * MovieObject.scala
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
 * Data Object used for representation of a YIFY movie object.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 11/03/14
 */
class MovieObject {

	private var _movieID: Option[String] = None

	def MovieID: Option[String] = _movieID
	def MovieID_=(value: String) { _movieID = Some(value) }

	private var _movieState: Option[String] = None

	def State: Option[String] = _movieState
	def State_=(value: String) { _movieState = Some(value) }

	private var _movieUrl: Option[String] = None

	def MovieUrl: Option[String] = _movieUrl
	def MovieUrl_=(value: String) { _movieUrl = Some(value) }

	private var _movieTitleClean: Option[String] = None

	def MovieTitleClean: Option[String] = _movieTitleClean
	def MovieTitleClean_=(value: String) = { _movieTitleClean = Some(value) }

	private var _movieYear: Int = 0

	def MovieYear:Int = _movieYear
	def MovieYear_=(value: Int) { _movieYear = value }

	private var _dateUploaded: Option[String] = None

	def DateUploaded: Option[String] = _dateUploaded
	def DateUploaded_=(value: String) { _dateUploaded = Some(value) }

	private var _dateUploadedEpoch: Long = 0

	def DateUploadedEpoch: Long = _dateUploadedEpoch
	def DateUploadedEpoch_=(value: Long) { _dateUploadedEpoch = value }

	private var _quality: Option[String] = None

	def Quality: Option[String] = _quality
	def Quality_=(value: String) { _quality = Some(value) }

	private var _coverImage: Option[String] = None

	def CoverImage: Option[String] = _coverImage
	def CoverImage_=(value: String) { _coverImage = Some(value) }

	private var _imdbCode: Option[String] = None

	def ImdbCode: Option[String] = _imdbCode
	def ImdbCode_=(value: String) { _imdbCode = Some(value) }

	private var _imdbLink: Option[String] = None

	def ImdbLink: Option[String] = _imdbLink
	def ImdbLink_=(value: String) { _imdbLink = Some(value) }

	private var _size: Option[String] = None

	def Size: Option[String] = _size
	def Size_=(value: String) { _size = Some(value) }

	private var _sizeByte: Long = 0

	def SizeByte: Long = _sizeByte
	def SizeByte_=(value: Long) { _sizeByte = value }

	private var _movieRating: Option[String] = None

	def MovieRating: Option[String] = _movieRating
	def MovieRating_=(value: String) { _movieRating = Some(value) }

	private var _genre: Option[String] = None

	def Genre: Option[String] = _genre
	def Genre_=(value: String) { _genre = Some(value) }

	private var _uploader: Option[String] = None

	def Uploader: Option[String] = _uploader
	def Uploader_=(value: String) { _uploader = Some(value) }

	private var _uploaderUid: Option[String] = None

	def UploaderUID: Option[String] = _uploaderUid
	def UploaderUID_=(value: String) { _uploaderUid = Some(value) }

	private var _torrentSeeds: Long = 0

	def TorrentSeeds: Long = _torrentSeeds
	def TorrentSeeds_=(value: Long) { _torrentSeeds = value }

	private var _downloaded: Long = 0

	def Downloaded: Long = _downloaded
	def Downloaded_=(value: Long) { _downloaded = value }

	private var _torrentPeers: Long = 0

	def TorrentPeers: Long = _torrentPeers
	def TorrentPeers_=(value: Long) { _torrentPeers = value }

	private var _torrentUrl: Option[String] = None

	def TorrentUrl: Option[String] = _torrentUrl
	def TorrentUrl_=(value: String) { _torrentUrl = Some(value) }

	private var _torrentHash: Option[String] = None

	def TorrentHash: Option[String] = _torrentHash
	def TorrentHash_=(value: String) { _torrentHash = Some(value) }

	private var _torrentMagnetUrl: Option[String] = None

	def TorrentMagnetUrl: Option[String] = _torrentMagnetUrl
	def TorrentMagnetUrl_=(value: String) { _torrentMagnetUrl = Some(value) }
}
