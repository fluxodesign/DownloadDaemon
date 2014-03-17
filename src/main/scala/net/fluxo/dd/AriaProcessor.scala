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
		// DEBUG
		System.out.println("Starting processRequest()...")
		// find a free port between starting rpc port to (starting rpc port + limit)
		var rpcPort = -1
		// DEBUG
		System.out.println("Starting RPC port: " + OUtils.readConfig.RPCPort)
		System.out.println("Ending RPC port: " + (OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit))
		for (x <- OUtils.readConfig.RPCPort to OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
			// DEBUG
			System.out.println("Checking port: " + x)
			if (!OUtils.portInUse(x)) {
				rpcPort = x
				break()
			}
		}
		// DEBUG
		System.out.println("Using port: " + rpcPort)
		if (rpcPort < 0) return "All download slots taken, try again later"
		var newGid = OUtils.generateGID()
		while (DbControl.isTaskGIDUsed(newGid)) newGid = OUtils.generateGID()
		// DEBUG
		System.out.println("new GID generated: " + newGid)
		new Thread(new AriaThread(rpcPort, uri, newGid, owner, true)).start()
		"OK " + newGid
	}

	def restartDownloads() {
		val activeTasks = DbControl.queryUnfinishedTasks()
		var rpcPort = -1
		for (t <- activeTasks) {
			LogWriter.writeLog("Resuming download for " + t.TaskGID.getOrElse(null), Level.INFO)
			for (x <- OUtils.readConfig.RPCPort to OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
				if (!OUtils.portInUse(x)) {
					rpcPort = x
					break()
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
			})
			process.waitFor()
		}
	}
}

object OAria extends AriaProcessor
