package net.fluxo.dd

import net.fluxo.dd.dbo.{Task, AriaProcess}
import java.util
import scala.util.control.Breaks._
import org.joda.time.DateTime
import org.apache.log4j.Level
import java.util.concurrent.TimeUnit
import org.apache.commons.io.FilenameUtils
import org.apache.commons.exec._
import scala.Some

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 19:57 PM
 */
class AriaProcessor {

	private val _activeProcesses: util.ArrayList[AriaProcess] = new util.ArrayList

	private val _startingProcesses: util.ArrayList[Int] = new util.ArrayList

	def ActiveProcesses: util.ArrayList[AriaProcess] = _activeProcesses

	def processRequest(uri: String, owner: String, isHttp: Boolean, httpUsername: String, httpPassword: String): String = {
		// the uri should always starts with "magnet:" or ends with ".torrent"
		if (!(uri startsWith "magnet:") && !(uri endsWith ".torrent")) {
			return "ERR URI"
		}
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
		val ariaThread = new AriaThread(rpcPort, uri, newGid, /*owner, false,*/ isHttp)
		if (httpUsername.length > 0 && httpPassword.length > 0) {
			ariaThread.setCredentials(httpUsername, httpPassword)
		}
		new Thread(ariaThread).start()
		stat(rpcPort, restarting = false, newGid, owner, uri, isHttp = isHttp, httpUsername, httpPassword, ariaThread getExecutor)
		"OK " + newGid
	}

	def killProcess(port: Int) {
		val iterator = _activeProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e AriaPort) == port) {
					e KillAriaProcess()
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
			val ariaThread = new AriaThread(rpcPort, t.TaskInput.getOrElse(null), t.TaskGID.getOrElse(null), t.TaskIsHttp)
			if (t.TaskIsHttp) {
				if (t.TaskHttpUsername.getOrElse("").length > 0 && t.TaskHttpPassword.getOrElse("").length > 0) {
					ariaThread.setCredentials(t.TaskHttpUsername.getOrElse(""), t.TaskHttpPassword.getOrElse(""))
				}
			}
			_startingProcesses add rpcPort
			new Thread(ariaThread).start()
			stat(rpcPort, restarting = true, t.TaskGID.getOrElse(""), t.TaskOwner.getOrElse(""), t.TaskInput.getOrElse(""),
				isHttp = t.TaskIsHttp, t.TaskHttpUsername.getOrElse(""), t.TaskHttpPassword.getOrElse(""), ariaThread getExecutor)
		}
	}

	def stat(port:Int, restarting: Boolean, gid: String, owner: String, uri: String, isHttp: Boolean,
	    httpUsername: String, httpPassword: String, executor: DefaultExecutor) {

		// we sleep for 3s, to allow the newly started process to settle...
		try { Thread sleep 3000 } catch { case ie: InterruptedException => }
		if (!restarting) {
			// DEBUG
			LogWriter writeLog ("Adding new task to DB", Level.DEBUG)
			DbControl.addTask(new Task {
				TaskGID_=(gid)
				TaskInput_=(uri)
				TaskOwner_=(owner)
				TaskStarted_=(DateTime.now().getMillis)
				TaskIsHttp_=(isHttp)
				if (httpUsername.length > 0 && httpPassword.length > 0) {
					TaskHttpUsername_=(httpUsername)
					TaskHttpPassword_=(httpPassword)
				}
			})
		}
		// DEBUG
		LogWriter writeLog ("Adding new active task to list...", Level.DEBUG)
		ActiveProcesses.add(new AriaProcess {
			AriaPort_=(port)
			AriaProcess_:(executor)
			AriaTaskGid_=(gid)
			AriaTaskRestarting_=(restarting)
			AriaHttpDownload_=(isHttp)
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
	}

	class AriaThread(port: Int, uri: String, gid: String, isHttp: Boolean) extends Runnable {
		private var _httpUsername: Option[String] = None
		private var _httpPassword: Option[String] = None

		def setCredentials(username: String, password: String) {
			_httpUsername = Some(username)
			_httpPassword = Some(password)
		}

		private var _executor: Option[DefaultExecutor] = None

		def getExecutor: DefaultExecutor = {
			_executor.getOrElse(null)
		}

		override def run() {
			OUtils createUriFile (gid, uri)
			// DEBUG
			LogWriter writeLog("AriaProcessor STARTING!", Level.DEBUG)
			val sb = new StringBuilder
			sb.append("aria2c").append(" --enable-rpc").append(" --rpc-listen-port=").append(port)
				.append(" --gid=").append(gid)
			if (isHttp && _httpUsername.getOrElse("").length > 0 && _httpPassword.getOrElse("").length > 0) {
				sb.append(" --http-user=").append(_httpUsername.getOrElse(""))
					.append(" --http-passwd=").append(_httpPassword.getOrElse(""))
			} else if (!isHttp) {
				sb.append(" --seed-time=0").append(" --max-overall-upload-limit=1").append(" --follow-torrent=mem")
					.append(" --seed-ratio=1")
			}
			sb.append(" --input-file=").append("uridir/").append(gid).append(".txt")
			// DEBUG
			LogWriter writeLog("command line: " + sb.toString(), Level.DEBUG)
			val cmdLine = CommandLine parse sb.toString()

			val watchdog = new ExecuteWatchdog(ExecuteWatchdog INFINITE_TIMEOUT)
			val executor = new DefaultExecutor
			executor setWatchdog watchdog
			_executor = Some(executor)
			val pumpsh = new PumpStreamHandler(new OStream)
			executor setStreamHandler pumpsh
			executor execute cmdLine
		}
	}

	class OStream extends LogOutputStream {
		override def processLine(line: String, level: Int) {
			LogWriter writeLog("AriaProcessor: " + line, Level.INFO)
		}
	}
}

object OAria extends AriaProcessor
