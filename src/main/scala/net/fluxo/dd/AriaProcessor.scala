/*
 * AriaProcessor.scala
 *
 * Copyright (c) 2014 Ronald Kurniawan. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.fluxo.dd

import java.io.ByteArrayOutputStream
import java.util
import java.util.concurrent.TimeUnit

import net.fluxo.dd.dbo.{AriaProcess, Task}
import org.apache.commons.exec._
import org.apache.commons.io.FilenameUtils
import org.apache.log4j.Level
import org.joda.time.DateTime

import scala.util.control.Breaks._

/**
 * AriaProcessor process commands that deal with "aria2c". It also monitors the currently running download. Whenever a
 * download process is stopped for any reason, this class also restarts the download. AriaProcessor also cleans up finished
 * downloads and moves the download results to local target directory.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 15/03/14
 */
class AriaProcessor {

	private val _activeProcesses: util.ArrayList[AriaProcess] = new util.ArrayList

	private val _startingProcesses: util.ArrayList[Int] = new util.ArrayList

	/**
	 * Return a list of active <code>net.fluxo.dd.dbo.AriaProcess</code>.
	 *
	 * @return <code>java.util.ArrayList</code> object
	 */
	def ActiveProcesses: util.ArrayList[AriaProcess] = _activeProcesses

	/**
	 * Process a download request from the client. Before the download starts, the process finds a free port to bind our
	 * aria2 process. If no free port is found, the process is aborted. Otherwise a new aria2 process is started.
	 *
	 * @param uri the URL (torrent/HTTP) to download
	 * @param owner user ID associated with this download
	 * @param isHttp is this a HTTP download?
	 * @param httpUsername username for HTTP authentication (supply an empty string if not required)
	 * @param httpPassword password for HTTP authentication (supply an empty string if not required)
	 * @return status string of the request; "OK" followed by download ID or error messages
	 */
	def processRequest(uri: String, owner: String, isHttp: Boolean, httpUsername: String, httpPassword: String,
		gzipped: Boolean = false): String = {
		if (!isHttp) {
			// the uri should always starts with "magnet:" or ends with ".torrent"
			if (!(uri startsWith "magnet:") && !(uri endsWith ".torrent")) {
				return "ERR URI"
			}
		} else {
			if (!(uri startsWith "http://") && !(uri startsWith "https://")) {
				return "ERR URI"
			}
		}
		// find a free port between starting rpc port to (starting rpc port + limit)
		var rpcPort = -1
		breakable {
			for (x <- OUtils.readConfig.RPCPort until OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
				if (!(OUtils portInUse x)) {
					rpcPort = x
					break()
				}
			}
		}
		if (rpcPort < 0) return "All download slots taken, try again later"
		var newGid = OUtils generateGID()
		while (DbControl isTaskGIDUsed newGid) newGid = OUtils generateGID()
		val ariaThread = new AriaThread(rpcPort, uri, newGid, isHttp)
		if (gzipped) ariaThread setFromKickass true
		if (httpUsername.length > 0 && httpPassword.length > 0) {
			ariaThread setCredentials(httpUsername, httpPassword)
		}
		new Thread(ariaThread) start()
		stat(rpcPort, restarting = false, newGid, owner, uri, isHttp = isHttp, httpUsername, httpPassword, ariaThread getExecutor)
		"OK " + newGid
	}

	/**
	 * Kill the aria2 process that is bound to a specified port.
	 *
	 * @param port the port number where the aria2 process is allegedly bound to
	 */
	def killProcess(port: Int) {
		val iterator = _activeProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if (e.AriaPort == port) {
					e killAriaProcess()
					_activeProcesses remove e
					break()
				}
			}
		}
	}

	/**
	 * Attempt to restart failed downloads.
	 */
	def restartDownloads() {
		val activeTasks = DbControl queryUnfinishedTasks()
		if (activeTasks.length > 0) LogWriter writeLog("Trying to restart " + activeTasks.length + " unfinished downloads...", Level.INFO)
		var rpcPort = -1
		for (t <- activeTasks) {
			LogWriter writeLog("Resuming download for " + t.TaskGID.orNull, Level.INFO)
			breakable {
				for (x <- OUtils.readConfig.RPCPort to OUtils.readConfig.RPCPort + OUtils.readConfig.RPCLimit) {
					if (!(OUtils portInUse x)) {
						rpcPort = x
						break()
					}
				}
			}
			if (rpcPort < 0) {
				LogWriter writeLog("All download slots taken, cannot restart downloads", Level.INFO)
				return
			}
			val ariaThread = new AriaThread(rpcPort, t.TaskInput.orNull, t.TaskGID.orNull, t.TaskIsHttp)
			if (t.TaskIsHttp) {
				if (t.TaskHttpUsername.getOrElse("").length > 0 && t.TaskHttpPassword.getOrElse("").length > 0) {
					ariaThread setCredentials(t.TaskHttpUsername.getOrElse(""), t.TaskHttpPassword.getOrElse(""))
				}
			}
			_startingProcesses add rpcPort
			new Thread(ariaThread) start()
			stat(rpcPort, restarting = true, t.TaskGID.getOrElse(""), t.TaskOwner.getOrElse(""), t.TaskInput.getOrElse(""),
				isHttp = t.TaskIsHttp, t.TaskHttpUsername.getOrElse(""), t.TaskHttpPassword.getOrElse(""), ariaThread getExecutor)
		}
	}

	/**
	 * Attempt to collect the statistics of a newly started aria2 process by querying its RPC port. If the call is
	 * successful, the method updates the database where clients can query the download progress.
	 * @param port port number where aria2 process is bound to
	 * @param restarting is this process a restart or a fresh download?
	 * @param gid ID for the download
	 * @param owner user ID associated with this download
	 * @param uri URL to download
	 * @param isHttp is this a HTTP download?
	 * @param httpUsername username for HTTP authentication (supply an empty string if not required)
	 * @param httpPassword password for HTTP authentication (supply an empty string if not required)
	 * @param executor a <code>org.apache.commons.exec.DefaultExecutor</code> object
	 */
	def stat(port:Int, restarting: Boolean, gid: String, owner: String, uri: String, isHttp: Boolean,
	    httpUsername: String, httpPassword: String, executor: DefaultExecutor) {

		// we sleep for 3s, to allow the newly started process to settle...
		try { Thread sleep 3000 } catch { case ie: InterruptedException => }
		if (!restarting) {
			// DEBUG
			LogWriter writeLog ("Adding new task to DB", Level.DEBUG)
			DbControl addTask new Task {
				TaskGID_=(gid)
				TaskInput_=(uri)
				TaskOwner_=(owner)
				TaskStarted_=(DateTime.now.getMillis)
				TaskIsHttp_=(isHttp)
				if (httpUsername.length > 0 && httpPassword.length > 0) {
					TaskHttpUsername_=(httpUsername)
					TaskHttpPassword_=(httpPassword)
				}
			}
		}
		// DEBUG
		LogWriter writeLog ("Adding new active task to list...", Level.DEBUG)
		ActiveProcesses add new AriaProcess {
			AriaPort_=(port)
			AriaProcess_:(executor)
			AriaTaskGid_=(gid)
			AriaTaskRestarting_=(restarting)
			AriaHttpDownload_=(isHttp)
			AriaTaskPid_=(findAriaTaskPid(gid))
			LogWriter writeLog("--> Task PID: " + AriaTaskPid, Level.DEBUG)
		}
		// set all necessary parameters if this is an HTTP download...
		if (isHttp) {
			DbControl updateTaskTailGID(gid, gid)
			try {
				TimeUnit.SECONDS.sleep(5)
			} catch {
				case ie: InterruptedException =>
			}
			val rpcClient = OUtils getXmlRpcClient port
			val active = OUtils sendAriaTellActive rpcClient
			if (active.length > 0) {
				for (o <- active) {
					val jMap = o.asInstanceOf[java.util.HashMap[String, Object]]
					val tailGID = (OUtils extractValueFromHashMap(jMap, "gid")).toString
					val task = {
						if (tailGID.length > 0) DbControl queryTaskTailGID tailGID else null
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
					DbControl updateTask task
				}
			}
		}
	}

	private def findAriaTaskPid(gid: String): String = {
		val command = new StringBuilder
		command append "ps aux | grep -e 'aria2c' | grep -e '" append gid append "' | awk {'print $2'}"
		val outputStream = new ByteArrayOutputStream
		val executor = new DefaultExecutor
		val commandLine = CommandLine parse command.toString
		val pumpsh = new PumpStreamHandler(outputStream)
		executor setStreamHandler pumpsh
		executor execute commandLine
		outputStream.toString.trim
	}

	/**
	 * AriaThread processes a new download process by calling aria2 through <code>DefaultExecutor</code>.
	 * @param port port number where aria2 process is bound to
	 * @param uri Torrent file URL to download
	 * @param gid ID for the download
	 * @param isHttp is this a HTTP download?
	 * @see java.lang.Runnable
	 */
	class AriaThread(port: Int, uri: String, gid: String, isHttp: Boolean) extends Runnable {
		private var _httpUsername: Option[String] = None
		private var _httpPassword: Option[String] = None
		private var _kickAss: Boolean = false

		def setFromKickass(value: Boolean) = { _kickAss = value }

		/**
		 * Set the username and password for HTTP authentication.
		 *
		 * @param username username (supply an empty string if not required)
		 * @param password password (supply an empty string if not required)
		 */
		def setCredentials(username: String, password: String) {
			_httpUsername = Some(username)
			_httpPassword = Some(password)
		}

		private var _executor: Option[DefaultExecutor] = None

		/**
		 * Return the <code>DefaultExecutor</code> for this process.
		 *
		 * @return a <code>org.apache.commons.exec.DefaultExecutor</code> object
		 */
		def getExecutor: DefaultExecutor = {
			_executor.orNull
		}

		/**
		 * Starts the download by constructing the command line first and then starts the <code>DefaultExecutor</code>.
		 */
		override def run() {
			// API v2 does not give the magnet URL easily, so we will download the .torrent file
			// and store it in the /uridir directory...
			//OUtils createUriFile (gid, uri)
			val torrentPath = "uridir/" + gid + ".torrent"
			OUtils crawlServerObject (uri, torrentPath, _kickAss)
			// DEBUG
			LogWriter writeLog("AriaProcessor STARTING!", Level.DEBUG)
			val sb = new StringBuilder
			sb append "aria2c" append " --disable-ipv6=true" append " --enable-rpc" append " --rpc-listen-port=" append port append " --gid=" append gid
			//sb append " --allow-overwrite=true --quiet=true"
			if (isHttp && (_httpUsername getOrElse "").length > 0 && (_httpPassword getOrElse "").length > 0) {
				sb append " --http-user=" append _httpUsername.getOrElse("") append " --http-passwd=" append _httpPassword.getOrElse("")
			} /*else if (!isHttp) {
				sb append " --seed-time=0" append " --max-overall-upload-limit=1" append " --follow-torrent=mem" append " --seed-ratio=0.1"
			}*/
			// API v2 forces us to use --torent-file parameter
			//sb append " --input-file=" append "uridir/" append gid append ".txt"
			sb append " --torrent-file=" append torrentPath
			// DEBUG
			LogWriter writeLog("command line: " + sb.toString(), Level.DEBUG)
			val cmdLine = CommandLine parse sb.toString()

			//val resultHandler = new DefaultExecuteResultHandler
			val watchdog = new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT)
			val executor = new DefaultExecutor
			executor setWatchdog watchdog
			_executor = Some(executor)
			val pumpsh = new PumpStreamHandler(new OStream)
			executor setStreamHandler pumpsh
			executor execute cmdLine
			//executor execute(cmdLine, resultHandler)

			/*resultHandler.waitFor()
			val exitCode = resultHandler.getExitValue
			if (!executor.isFailure(exitCode)) {
				// Success! clean up now!
				DbControl.finishTaskFromThread("success", gid)
			} */
		}
	}

	/**
	 * Process the output result from <code>DefaultExecutor</code> into the log.
	 */
	class OStream() extends LogOutputStream {
		override def processLine(line: String, level: Int) {
			if (((line trim) length) > 0) {
				LogWriter writeLog("Aria Processor: " + line, Level.INFO)
			}
		}
	}
}

/**
 * A Singleton object of AriaProcessor.
 */
object OAria extends AriaProcessor
