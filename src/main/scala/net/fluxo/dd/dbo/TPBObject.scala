package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 24/03/14
 * Time: 4:57 PM
 * Comment:
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

	private var _title: Option[String] = None

	def Title: Option[String] = _title
	def Title_:(value: String) { _title = Some(value) }

	private var _uploaded: Option[String] = None

	def Uploaded: Option[String] = _uploaded
	def Uploaded_=(value: String) { _uploaded = Some(value) }

	private var _size: Option[String] = None

	def Size: Option[String] = _size
	def Size_=(value: String) { _size = Some(value) }

	private var _uploadedBy: Option[String] = None

	def Uploader: Option[String] = _uploadedBy
	def Uploader_=(value: String) { _uploadedBy = Some(value) }

	private var _magnetURL: Option[String] = None

	def MagnetURL: Option[String] = _magnetURL
	def MagnetURL_:(value: String) { _magnetURL = Some(value) }

	private var _torrentURL: Option[String] = None

	def TorrentURL: Option[String] = _torrentURL
	def TorrentURL_=(value: String) { _torrentURL = Some(value) }
}
