package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/03/14
 * Time: 5:53 PM
 */
class Task {

	private var _gid: Option[String] = None

	def TaskGID: Option[String] = _gid
	def TaskGID_= (value: String) {
		_gid = Some(value)
	}

	private var _input: Option[String] = None

	def TaskInput: Option[String] = _input
	def TaskInput_= (value: String) {
		_input = Some(value)
	}

	private var _started: Long = 0

	def TaskStarted: Long = _started
	def TaskStarted_= (value: Long) {
		_started = value
	}

	private var _ended: Long = 0

	def TaskEnded: Long = _ended
	def TaskEnded_= (value: Long) {
		_ended = value
	}

	private var _completed: Boolean = false

	def IsTaskCompleted: Boolean = _completed
	def IsTaskCompleted_= (value: Boolean) {
		_completed = value
	}

	private var _owner: Option[String] = None

	def TaskOwner: Option[String] = _owner
	def TaskOwner_= (value: String) {
		_owner = Some(value)
	}

	private var _directory: Option[String] = None

	def TaskDirectory: Option[String] = _directory
	def TaskDirectory_= (value: String) {
		_directory = Some(value)
	}

	private var _file: Option[String] = None

	def TaskFile: Option[String] = _file
	def TaskFile_= (value: String) {
		_file = Some(value)
	}

	private var _status: Option[String] = None

	def TaskStatus: Option[String] = _status
	def TaskStatus_= (value: String) {
		_status = Some(value)
	}

	private var _totalLength: Long = 0

	def TaskTotalLength: Long = _totalLength
	def TaskTotalLength_= (value: Long) {
		_totalLength = value
	}

	private var _completedLength: Long = 0

	def TaskCompletedLength: Long = _completedLength
	def TaskCompletedLength_= (value: Long) {
		_completedLength = value
	}
}
