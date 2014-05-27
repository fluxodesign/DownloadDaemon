/*
 * VideoProcessor.scala
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

import java.util
import org.apache.commons.exec._
import org.apache.log4j.Level
import scala.Some
import net.fluxo.dd.dbo.{VideoProcess, Task}
import org.joda.time.DateTime
import scala.util.control.Breaks._
import java.util.regex.Pattern
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * VideoProcessor process commands that deal with youtube-dl. It also monitors running downloads and tries to
 * restart the failed process(es).
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 26/05/14.
 */
class VideoProcessor {

	private val _activeProcesses: util.ArrayList[VideoProcess] = new util.ArrayList[VideoProcess]()

	/**
	 * Return a list of active video download processes.
	 *
	 * @return <code>java.util.Arraylist</code> object
	 */
	def ActiveProcesses: util.ArrayList[VideoProcess] = _activeProcesses

	/**
	 * Process a video download request from the client.
	 *
	 * @param url Video URL to download
	 * @return status string of the request; "OK" followed by the download ID
	 */
	def processRequest(url: String, owner: String): String = {
		var newGid = OUtils generateGID()
		while (DbControl isTaskGIDUsed newGid) newGid = OUtils generateGID()
		val videoThread = new VideoThread(url, newGid)
		new Thread(videoThread) start()
		stat(newGid, owner, url, videoThread getExecutor)
		"OK " + newGid
	}

	/**
	 * This method should only be called from `restartDownload()`; it restarts the download process for a particular
	 * video.
	 *
	 * @param url the URL of the video
	 * @param owner user ID of the downloader
	 * @param gid unique ID for the download
	 * @return status string of the request; "OK" followed by the download ID
	 */
	def reprocessRequest(url: String, owner: String, gid: String): String = {
		val videoThread = new VideoThread(url, gid)
		new Thread(videoThread) start()
		stat(gid, owner, url, videoThread getExecutor)
		"OK " + gid
	}

	/**
	 * Attempt to collect the statistics of a new download.
	 *
	 * @param gid unique ID for the download
	 * @param owner user ID of the downloader
	 * @param url URL of the video
	 * @param executor a <code>org.apache.commons.exec.DefaultExecutor</code> object
	 */
	def stat(gid: String, owner: String, url: String, executor: DefaultExecutor) {
		DbControl addTask new Task {
			TaskGID_=(gid)
			TaskInput_=(url)
			TaskOwner_=(owner)
			TaskStarted_=(DateTime.now.getMillis)
			TaskIsHttp_=(value = false)
			TaskTailGID_=("notailgid")
			TaskInfoHash_=("noinfohash")
		}
		// DEBUG
		LogWriter writeLog("Adding new video download task to list...", Level.DEBUG)
		ActiveProcesses add new VideoProcess {
			VideoTaskGid_=(gid)
			VideoProcess_=(executor)
			VideoTaskRestarting_=(value = false)
			VideoLastUpdated_=(DateTime.now.getMillis)
			Owner_:(owner)
		}
	}

	/**
	 * Attempt to restart unfinished downloads.
	 */
	def restartDownload() {
		// we need to clean everything up and start over
		ActiveProcesses clear()
		val arrUnfinishedTasks = DbControl queryActiveVideoTask()
		if ((arrUnfinishedTasks length) > 0) {
			for (t <- arrUnfinishedTasks) {
				FileUtils.forceDelete(new File(t.TaskPackage.getOrElse("")))
				reprocessRequest(t.TaskInput.getOrElse(""), t.TaskOwner.getOrElse(""), t.TaskGID.getOrElse(""))
			}
		}
	}

	/**
	 * Return the owner of a particular video download task.
	 *
	 * @param gid unique ID of the download
	 * @return the user ID of the downloader
	 */
	def getOwner(gid: String): String = {
		var owner: Option[String] = None
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).getOrElse(null) equals gid) {
					owner = Some((e Owner).getOrElse(null))
					break()
				}
			}
		}
		owner getOrElse ""
	}

	/**
	 * Kill the youtube-dl process tied to this download.
	 *
	 * @param gid unique ID of the download
	 */
	def killProcess(gid: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).getOrElse(null) equals gid) {
					e killVideoProcess()
					iterator remove()
					break()
				}
			}
		}
	}

	/**
	 * Remove a task with specified GID from active list process.
	 *
	 * @param gid unique ID of the download
	 */
	def removeFromList(gid: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).getOrElse(null) equals gid) {
					iterator remove()
					break()
				}
			}
		}
	}

	/**
	 * Update the timestamp on our download tracker object to the time of the last stat update.
	 *
	 * @param taskGID unique ID of the download
	 */
	def updateTimestamp(taskGID: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).getOrElse(null) equals taskGID) {
					e VideoLastUpdated_= DateTime.now.getMillis
					break()
				}
			}
		}
	}

	/**
	 * VideoThread processes a new download process by calling youtube-dl through <code>DefaultExecutor</code>.
	 * @param url Video URL to download
	 */
	class VideoThread(url: String, gid: String) extends Runnable {

		private var _executor: Option[DefaultExecutor] = None

		/**
		 * Return the <code>DefaultExecutor</code> for this process.
		 *
		 * @return a <code>org.apache.commons.exec.DefaultExecutor</code> object
		 */
		def getExecutor: DefaultExecutor = {
			_executor getOrElse null
		}

		/**
		 * Starts the download by constructing the command line first and then starts the <code>DefaultExecutor</code>.
		 */
		override def run() {
			OUtils createUriFile(gid: String, url: String)
			// DEBUG
			LogWriter writeLog("Youtube-dl STARTING!", Level.DEBUG)
			// if the "output" file exists, delete it first
			val outputFile = new File("./uridir/" + gid + ".output")
			if (outputFile.exists) outputFile.delete
			val sb = new StringBuilder
			sb append "youtube-dl" append " " append url  append " > uridir/" append gid append ".output"
			val commandLine = CommandLine parse sb.toString
			val watchdog = new ExecuteWatchdog(ExecuteWatchdog INFINITE_TIMEOUT)
			val executor = new DefaultExecutor
			executor setWatchdog watchdog
			_executor = Some(executor)
			val pumpsh = new PumpStreamHandler(new VOStream(gid))
			executor setStreamHandler pumpsh
			executor execute commandLine
		}
	}

	/**
	 * Process the output result from <code>DefaultExecutor</code> into appropriate target(s).
	 */
	class VOStream(taskGID: String) extends LogOutputStream {
		private var _inputTitle = "N/A"
		private var _progressPercentage = 0.0d
		private val _pattern = Pattern compile "[\\d+.]"
		private var _totalFileLength = 0L

		/**
		 * This method is overriden. It calls the `updateDownloadProgress(String)` method.
		 *
		 * @param line the last string received from the app
		 * @param level not used
		 */
		override def processLine(line: String, level: Int) {
			updateDownloadProgress(line)
			LogWriter writeLog("Youtube-dl Process: " + line, Level.INFO)
		}

		/**
		 * This method tracks and updates the progress of the download. When it finishes, the method would do the cleanup and
		 * moves the finished download into target directory.
		 *
		 * @param line the string output to be analysed
		 */
		private def updateDownloadProgress(line: String) {
			if (line startsWith "[download] Destination:") {
				_inputTitle = line replace("[download] Destination:", "")
			} else if ((line contains "[download]") && (line contains "% of") && (line contains "ETA")) {
				val strProgress = (line replace("[download]", "")).trim
				val idxPercent = strProgress indexOf "%"
				if (idxPercent > -1) _progressPercentage = java.lang.Double.parseDouble(strProgress.substring(0, idxPercent))
				val idx1 = (strProgress indexOf "of") + 2
				val idx2 = strProgress indexOf "at"
				val strTotal = (strProgress substring(idx1, idx2)).trim
				val matcher = _pattern matcher strTotal
				val sb = new StringBuilder
				while (matcher.find) {
					val start = matcher.start
					val end = matcher.end
					sb append strTotal substring(start, end)
				}
				val iTotal = java.lang.Double.parseDouble(sb toString())
				if ((strTotal indexOf "KiB") > -1 && iTotal > 0) {
					_totalFileLength = (iTotal * 1024).toLong
				} else if ((strTotal indexOf "MiB") > -1 && iTotal > 0) {
					_totalFileLength = (iTotal * 1024 * 1024).toLong
				} else if ((strTotal indexOf "GiB") > -1 && iTotal > 0) {
					_totalFileLength = (iTotal * 1024 * 1024 * 1024).toLong
				}
			}
			// update the database
			val status = {
				if (_progressPercentage == 100) "complete"
				else "active"
			}
			val completedLength = ((_progressPercentage / 100) * _totalFileLength).toLong
			DbControl updateVideoTask(taskGID, getOwner(taskGID), _inputTitle, status, _totalFileLength, completedLength)
			updateTimestamp(taskGID)
			// time to remove this task from our list...
			if (_progressPercentage == 100) {
				removeFromList(taskGID)
				val destFile = new File(OUtils.readConfig.DownloadDir.getOrElse("") + "/" + _inputTitle)
				if (!destFile.exists()) {
					FileUtils moveFile(new File(_inputTitle), destFile)
				}
			}
		}
	}
}

/**
 * A singleton object for VideoProcessor.
 */
object OVideoP extends VideoProcessor
