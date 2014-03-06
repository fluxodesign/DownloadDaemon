package net.fluxo.dd.dbo

/**
 * User: viper
 * Date: 6/03/14
 * Time: 11:07 AM
 *
 */
class TaskStatus {

	private var _status: Option[String] = None

	def status: Option[String] = _status
	def status_=(value: String) = {
		_status = Some(value)
	}

	private var _totalLength: Long = 0

	def totalLength: Long = _totalLength
	def totalLength_=(value: Long) = {
		_totalLength = value
	}

	private var _completedLength: Long = 0

	def completedLength: Long = _completedLength
	def completedLength_=(value: Long) = {
		_completedLength = value
	}

	private var _downloadSpeed: Int = 0

	def downloadSpeed: Int = _downloadSpeed
	def downloadSpeed_=(value: Int) = {
		_downloadSpeed = value
	}

	private var _numSeeders: Int = 0

	def numSeeders: Int = _numSeeders
	def numSeeders_=(value: Int) = {
		_numSeeders = value
	}

	private var _pieceLength: Long = 0

	def pieceLength: Long = _pieceLength
	def pieceLength_=(value: Long) = {
		_pieceLength = value
	}

	private var _numPieces: Int = 0

	def numPieces: Int = _numPieces
	def numPieces_=(value: Int) = {
		_numPieces = value
	}
}
