package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/03/14
 * Time: 5:53 PM
 */
class Task {

	private var _gid: Option[String] = None

	def TaskGID: Option[String] = _gid
	def TaskGID_= (value: String) = {
		_gid = Some(value)
	}

	private var _input: Option[String] = None

	def TaskInput: Option[String] = _input
	def TaskInput_= (value: String) = {
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

	private var _rpcPort: Int = 6800

	def TaskRPCPort: Int = _rpcPort
	def TaskRPCPort_= (value: Int) {
		_rpcPort = value
	}
}
