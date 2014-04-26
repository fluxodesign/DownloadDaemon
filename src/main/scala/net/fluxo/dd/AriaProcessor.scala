package net.fluxo.dd

import net.fluxo.dd.dbo.{Task, AriaProcess}
import java.util
import scala.util.control.Breaks._
import org.joda.time.DateTime
import org.apache.log4j.Level
import java.util.concurrent.TimeUnit
import org.apache.commons.io.FilenameUtils

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 19:57 PM
 */
class AriaProcessor {

	private val _activeProcesses: util.ArrayList[AriaProcess] = new util.ArrayList

	def ActiveProcesses: util.ArrayList[AriaProcess] = _activeProcesses

	def processRequest(uri: String, owner: String, isHttp: Boolean, httpUsername: String, httpPassword: String): String = {
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
		val ariaThread = new AriaThread(rpcPort, uri, newGid, owner, false, isHttp)
		if (httpUsername.length > 0 && httpPassword.length > 0) {
			ariaThread.setCredentials(httpUsername, httpPassword)
		}
		new Thread(ariaThread).start()
		"OK " + newGid
	}

	def killProcess(port: Int) {
		val iterator = ActiveProcesses.iterator()
		breakable {
			while (iterator.hasNext) {
				val o = iterator.next()
				if (o.AriaPort == port) {
					val process = o.AriaProcess.getOrElse(null)
					o.AriaTaskRestarting_=(value = true)
					process.destroy()
					iterator.remove()
					break()
				}
			}
		}
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
			val ariaThread = new AriaThread(rpcPort, t.TaskInput.getOrElse(null), t.TaskGID.getOrElse(null),
				t.TaskOwner.getOrElse(null), true, t.TaskIsHttp)
			if (t.TaskIsHttp) {
				if (t.TaskHttpUsername.getOrElse("").length > 0 && t.TaskHttpPassword.getOrElse("").length > 0) {
					ariaThread.setCredentials(t.TaskHttpUsername.getOrElse(""), t.TaskHttpPassword.getOrElse(""))
				}
			}
			new Thread(ariaThread).start()
		}
	}

	class AriaThread(port: Int, uri: String, gid: String, owner: String, restarting: Boolean, isHttp: Boolean) extends Runnable {
		private var _httpUsername: Option[String] = None
		private var _httpPassword: Option[String] = None

		def setCredentials(username: String, password: String) {
			_httpUsername = Some(username)
			_httpPassword = Some(password)
		}

		override def run() {
			val process = {
				if (_httpUsername.getOrElse("").length > 0 && _httpPassword.getOrElse("").length > 0) {
					new ProcessBuilder("aria2c", "--enable-rpc", "--rpc-listen-port=" + port,
						"--gid=" + gid, "--http-user=" + _httpUsername.getOrElse(""),
						"--http-passwd=" + _httpPassword.getOrElse(""), uri).start()
				} else {
					new ProcessBuilder("aria2c", "--enable-rpc", "--rpc-listen-port=" + port,
						"--seed-time=0", "--max-overall-upload-limit=1", "--follow-torrent=mem",
						"--gid=" + gid, "--seed-ratio=0.1", uri).start()
				}
			}

			val taskPid = {
				if ((process getClass).getName.equals("java.lang.UNIXProcess")) {
					try {
						val field = (process getClass).getDeclaredField("pid")
						field setAccessible true
						field getInt process
					} catch {
						case e: Exception => -1
					}
				}
				-1
			}

			// DEBUG
			LogWriter writeLog("ARIA PID: " + taskPid, Level.DEBUG)

			// For DEBUG purposes only, to read ARIA2 output...
			/*val br = new BufferedReader(new InputStreamReader(process.getInputStream))
			var line = br readLine()
			while (line != null) {
				LogWriter writeLog("ARIA2: " + line, Level.INFO)
				line = br readLine()
			}*/

			if (!restarting) {
				DbControl.addTask(new Task {
					TaskGID_=(gid)
					TaskInput_=(uri)
					TaskOwner_=(owner)
					TaskStarted_=(DateTime.now().getMillis)
					TaskIsHttp_=(isHttp)
					if (_httpUsername.getOrElse("").length > 0 && _httpPassword.getOrElse("").length > 0) {
						TaskHttpUsername_=(_httpUsername.getOrElse(""))
						TaskHttpPassword_=(_httpPassword.getOrElse(""))
					}
				})
			}
			ActiveProcesses.add(new AriaProcess {
				AriaPort_=(port)
				AriaProcess_=(process)
				AriaTaskGid_=(gid)
				AriaTaskRestarting_=(restarting)
				AriaHttpDownload_=(isHttp)
				AriaTaskPID_=(taskPid)
			})
			// set all necessary parameters if this is an HTTP download...
			if (isHttp) {
				DbControl.updateTaskTailGID(gid, gid)
				try {
					TimeUnit.SECONDS.sleep(5)
				} catch {
					case ie: InterruptedException =>
				}
				val rpcClient = OUtils.getXmlRpcClient(port)
				val active = OUtils.sendAriaTellActive(rpcClient)
				if (active.length > 0) {
					for (o <- active) {
						val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
						val tailGID = OUtils.extractValueFromHashMap(jMap, "gid").toString
						val task = {
							if (tailGID.length > 0) DbControl.queryTaskTailGID(tailGID) else null
						}
						val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
						task.TaskCompletedLength_=(cl)
						val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
						task.TaskTotalLength_=(tl)
						task.TaskStatus_=(OUtils.extractValueFromHashMap(jMap, "status").toString)
						task.TaskInfoHash_=("noinfohash")
						// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
						val objFiles = OUtils.extractValueFromHashMap(jMap, "files").asInstanceOf[Array[Object]]
						if (objFiles.length > 0) {
							val files = objFiles(0).asInstanceOf[java.util.HashMap[String, Object]]
							val path = OUtils.extractValueFromHashMap(files, "path").asInstanceOf[String]
							task.TaskPackage_=(FilenameUtils.getName(path))
						}
						val progress = (task.TaskCompletedLength * 100)/task.TaskTotalLength
						LogWriter writeLog ("UPDATE: " + ((task TaskPackage) getOrElse "") + " --> " + progress + "%", Level.INFO)
						DbControl.updateTask(task)
					}
				}
			}
			process.waitFor()
		}
	}
}

object OAria extends AriaProcessor
