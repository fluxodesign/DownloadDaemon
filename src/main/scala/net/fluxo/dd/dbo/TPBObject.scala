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
}
