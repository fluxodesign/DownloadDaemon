package net.fluxo.dd.dbo

/**.
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 20:05 PM
 */
class AriaProcess {

	private var _port: Int = 0

	def AriaPort: Int = _port
	def AriaPort_=(value: Int) { _port = value }

	private var _process: Process = null

	def AriaProcess: Process = _process
	def AriaProcess_=(value: Process) { _process = value }
}
