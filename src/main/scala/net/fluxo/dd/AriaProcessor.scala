package net.fluxo.dd

import net.fluxo.dd.dbo.AriaProcess
import java.util
import scala.util.control.Breaks._

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 19:57 PM
 */
class AriaProcessor {

	private val _activeProcesses: util.ArrayList[AriaProcess] = new util.ArrayList

	def ActiveProcesses(): util.ArrayList[AriaProcess] = _activeProcesses

	def processRequest(uri: String): String = {
		// find a free port between starting rpc port to (starting rpc port + limit)
		var rpcPort = -1
		for (x <- OUtils.ReadConfig.RPCPort to OUtils.ReadConfig.RPCPort + OUtils.ReadConfig.RPCLimit) {
			if (!OUtils.PortInUse(x)) {
				rpcPort = x
				break
			}
		}
		if (rpcPort < 0) return "All download slots taken, try again later"
		new Thread(new AriaThread(rpcPort, uri)).start()
	}

	private

	class AriaThread(port: Int, uri: String) extends Runnable {
		override def run() {
			val process = new ProcessBuilder("aria2c", "--enable-rpc", "--rpc-listen-port=" + port,
				"--seed-time=0", "--max-overall-upload-limit=1", "--follow-torrent=mem",
				"--seed-ratio=0.1", "--rpc-listen-all=false", uri).start()
			process.waitFor()
		}
	}
}

object OAria extends AriaProcessor