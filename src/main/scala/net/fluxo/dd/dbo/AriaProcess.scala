package net.fluxo.dd.dbo

import org.apache.commons.exec.Executor

/**.
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 20:05 PM
 */
class AriaProcess {

	private var _port: Int = 0

	def AriaPort: Int = _port
	def AriaPort_=(value: Int) { _port = value }

	//private var _process: Option[Process] = None

	/*def AriaProcess: Option[Process] = _process
	def AriaProcess_=(value: Process) { _process = Some(value) }
	def NullifyProcess() { _process = None }*/

	private var _process: Option[Executor] = None

	def AriaProcess: Option[Executor] = _process
	def AriaProcess_:(value: Executor) { _process = Some(value) }
	def KillAriaProcess() {
		_process.getOrElse(null).getWatchdog.destroyProcess()
		_process = None
	}

	private var _gid: Option[String] = None

	def AriaTaskGid: Option[String] = _gid
	def AriaTaskGid_=(value: String) { _gid = Some(value) }

	private var _isRestarting: Boolean = false

	def AriaTaskRestarting: Boolean = _isRestarting
	def AriaTaskRestarting_=(value: Boolean) { _isRestarting = value }

	private var _httpDownload: Boolean = false

	def AriaHttpDownload: Boolean = _httpDownload
	def AriaHttpDownload_=(value: Boolean) { _httpDownload = value }

	/*private var _taskPid: Int = 0

	def AriaTaskPID: Int = _taskPid
	def AriaTaskPID_=(value: Int) { _taskPid = value }*/
}
