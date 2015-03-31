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
 * @version 0.4.5, 11/03/14
 */
class MovieObject {

	private var _movieID: Long = 0L

	def MovieID: Long = _movieID
	def MovieID_=(value: Long) { _movieID = value }

	private var _movieState: Option[String] = None

	def State: Option[String] = _movieState
	def State_=(value: String) { _movieState = Some(value) }

	private var _movieUrl: Option[String] = None

	def MovieUrl: Option[String] = _movieUrl
	def MovieUrl_=(value: String) { _movieUrl = Some(value) }

	private var _movieTitle: Option[String] = None

	def MovieTitle: Option[String] = _movieTitle
	def MovieTitle_=(value: String) { _movieTitle = Some(value) }

	private var _movieTitleLong: Option[String] = None

	def MovieTitleLong: Option[String] = _movieTitleLong
	def MovieTitleLong_=(value: String) = { _movieTitleLong = Some(value) }

	private var _movieYear: Long = 0

	def MovieYear:Long = _movieYear
	def MovieYear_=(value: Long) { _movieYear = value }

	private var _dateUploaded: Option[String] = None

	def DateUploaded: Option[String] = _dateUploaded
	def DateUploaded_=(value: String) { _dateUploaded = Some(value) }

	private var _dateUploadedEpoch: Long = 0L

	def DateUploadedEpoch: Long = _dateUploadedEpoch
	def DateUploadedEpoch_=(value: Long) { _dateUploadedEpoch = value }

	private var _coverImage: Option[String] = None

	def CoverImage: Option[String] = _coverImage
	def CoverImage_=(value: String) { _coverImage = Some(value) }

	private var _imdbCode: Option[String] = None

	def ImdbCode: Option[String] = _imdbCode
	def ImdbCode_=(value: String) { _imdbCode = Some(value) }

	private var _imdbLink: Option[String] = None

	def ImdbLink: Option[String] = {
		if (_imdbCode.isDefined) {
			_imdbLink = Some("http://www.imdb.com/title/" + _imdbCode + "/")
		}
		_imdbLink
	}

	private var _movieRating: Double = 0.0D

	def MovieRating: Double = _movieRating
	def MovieRating_=(value: Double) { _movieRating = value }

	private var _movieRuntime: Long = 0L

	def MovieRuntime: Long = _movieRuntime
	def MovieRutime_=(value: Long) { _movieRuntime = value }

	private var _mpaRating: Option[String] = None

	def MpaRating: Option[String] = _mpaRating
	def MpaRating_=(value: String) { _mpaRating = Some(value) }

	private var _language: Option[String] = None

	def Language: Option[String] = _language
	def Language_=(value: String) { _language = Some(value) }

	private var _genre: Option[String] = None

	def Genre: Option[String] = _genre
	def Genre_=(value: String) { _genre = Some(value) }


	private var _quality720p: Option[String] = None
	private var _quality1080p: Option[String] = None
	private var _quality3D: Option[String] = None

	def Quality720p: Option[String] = _quality720p
	def Quality720p_=(value: String) { _quality720p = Some(value) }

	def Quality1080p: Option[String] = _quality1080p
	def Quality1080p_=(value: String) { _quality1080p = Some(value) }

	def Quality3D: Option[String] = _quality3D
	def Quality3D_=(value: String) { _quality3D = Some(value) }

	private var _torrentSeeds720p: Long = 0L
	private var _torrentSeeds1080p: Long = 0L
	private var _torrentSeeds3D: Long = 0L

	def TorrentSeeds720p: Long = _torrentSeeds720p
	def TorrentSeeds720p_=(value: Long) { _torrentSeeds720p = value }

	private var _downloadCount: Long = 0L
	private var _downloaded720p: Long = 0L
	private var _downloaded1080p: Long = 0L
	private var _downloaded3D: Long = 0L

	def DownloadCount: Long = _downloadCount
	def DownloadCount_=(value: Long) { _downloadCount = value }

	private var _torrentPeers720p: Long = 0L
	private var _torrentPeers1080p: Long = 0L
	private var _torrentPeers3D: Long = 0L

	def TorrentPeers720p: Long = _torrentPeers720p
	def TorrentPeers720p_=(value: Long) { _torrentPeers720p = value }

	private var _torrentUrl720p: Option[String] = None
	private var _torrentUrl1080p: Option[String] = None
	private var _torrentUrl3D: Option[String] = None

	def TorrentUrl720p: Option[String] = _torrentUrl720p
	def TorrentUrl720p_=(value: String) { _torrentUrl720p = Some(value) }

	private var _torrentHash720p: Option[String] = None
	private var _torrentHash1080p: Option[String] = None
	private var _torrentHash3D: Option[String] = None

	def TorrentHash720p: Option[String] = _torrentHash720p
	def TorrentHash720p_=(value: String) { _torrentHash720p = Some(value) }

	private var _torrentMagnetUrl720p: Option[String] = None
	private var _torrentMagnetUrl1080p: Option[String] = None
	private var _torrentMagnetUrl3D: Option[String] = None

	def TorrentMagnetUrl720p: Option[String] = _torrentMagnetUrl720p
	def TorrentMagnetUrl720p_=(value: String) { _torrentMagnetUrl720p = Some(value) }

	private var _size720p: Option[String] = None
	private var _size1080p: Option[String] = None
	private var _size3D: Option[String] = None

	def Size720p: Option[String] = _size720p
	def Size720p_=(value: String) { _size720p = Some(value) }

	private var _sizeByte720p: Long = 0L

	def SizeByte720p: Long = _sizeByte720p
	def SizeByte720p_=(value: Long) { _sizeByte720p = value }

	private var _dateUploaded720p: Option[String] = None
	private var _dateUploaded1080p: Option[String] = None
	private var _dateUploaded3D: Option[String] = None

	private var _dateUploadedEpoch720p: Long = 0L
	private var _dateUploadedEpoch1080p: Long = 0L
	private var _dateUploadedEpoch3D: Long = 0L

	private var _resolution720p: Option[String] = None
	private var _resolution1080p: Option[String] = None
	private var _resolution3D: Option[String] = None

	private var _frameRate720p: Double = 0.0D
	private var _frameRate1080p: Double = 0.0D
	private var _frameRate3D: Double = 0.0D

}
