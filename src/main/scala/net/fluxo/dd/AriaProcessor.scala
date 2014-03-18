package net.fluxo.dd

import net.fluxo.dd.dbo.{Task, AriaProcess}
import java.util
import scala.util.control.Breaks._
import org.joda.time.DateTime
import org.apache.log4j.Level

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 19:57 PM
 */
class AriaProcessor {

	private val _activeProcesses: util.ArrayList[AriaProcess] = new util.ArrayList

	def ActiveProcesses: util.ArrayList[AriaProcess] = _activeProcesses

	def processRequest(uri: String, owner: String): String = {
		// find a free port between starting rpc port to (starting rpc port + limit)
		var rpcPort = -1
		breakable {
			for (x <- OUtils.readConfig.RPCPort until OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
				if (!OUtils.portInUse(x)) {
					rpcPort = x
					break()
				}
			}
		}
		if (rpcPort < 0) return "All download slots taken, try again later"
		var newGid = OUtils.generateGID()
		while (DbControl.isTaskGIDUsed(newGid)) newGid = OUtils.generateGID()
		new Thread(new AriaThread(rpcPort, uri, newGid, owner, false)).start()
		"OK " + newGid
	}

	def restartDownloads() {
		val activeTasks = DbControl.queryUnfinishedTasks()
		if (activeTasks.length > 0) LogWriter.writeLog("Trying to restart " + activeTasks.length + " unfinished downloads...", Level.INFO)
		var rpcPort = -1
		for (t <- activeTasks) {
			LogWriter.writeLog("Resuming download for " + t.TaskGID.getOrElse(null), Level.INFO)
			breakable {
				for (x <- OUtils.readConfig.RPCPort to OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
					if (!OUtils.portInUse(x)) {
						rpcPort = x
						break()
					}
				}
			}
			if (rpcPort < 0) {
				LogWriter.writeLog("All download slots taken, cannot restart downloads", Level.INFO)
				return
			}
			new Thread(new AriaThread(rpcPort, t.TaskInput.getOrElse(null), t.TaskGID.getOrElse(null), t.TaskOwner.getOrElse(null), true)).start()
		}
	}

	class AriaThread(port: Int, uri: String, gid: String, owner: String, restarting: Boolean) extends Runnable {
		override def run() {
			val process = new ProcessBuilder("aria2c", "--enable-rpc", "--rpc-listen-port=" + port,
				"--seed-time=0", "--max-overall-upload-limit=1", "--follow-torrent=mem",
				"--gid=" + gid, "--seed-ratio=0.1", "--rpc-listen-all=false", uri).start()

			if (!restarting) {
				DbControl.addTask(new Task {
					TaskGID_=(gid)
					TaskInput_=(uri)
					TaskOwner_=(owner)
					TaskStarted_=(DateTime.now().getMillis)
				})
			}
			ActiveProcesses.add(new AriaProcess {
				AriaPort_=(port)
				AriaProcess_=(process)
				AriaTaskGid_=(gid)
				AriaTaskRestarting_=(restarting)
			})
			process.waitFor()
		}
	}
}

object OAria extends AriaProcessor
