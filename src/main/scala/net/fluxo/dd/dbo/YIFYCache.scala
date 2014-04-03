package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 3/04/14 5:15PM
 * Comment:
 */
class YIFYCache {

	private var _movieID: Int = 0

	def MovieID: Int = _movieID
	def MovieID_:(value: Int) { _movieID = value }

	private var _title: Option[String] = None

	def MovieTitle: Option[String] = _title
	def MovieTitle_:(value: String) { _title = Some(value) }

	private var _year: Option[String] = None

	def MovieYear: Option[String] = _year
	def MovieYear_:(value: String) { _year = Some(value) }

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
