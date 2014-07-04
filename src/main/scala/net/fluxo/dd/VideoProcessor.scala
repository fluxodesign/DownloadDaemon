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
import net.fluxo.dd.dbo.VideoProcess
import org.joda.time.DateTime
import scala.util.control.Breaks._

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
		DbControl addVideoTask(gid, owner)
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
	 * Check whether a Task GID is in ActiveProcesses.
	 *
	 * @param tGID a unique task ID
	 * @return true if the task ID is in ActiveProcesses; false otherwise
	 */
	def isTaskGIDInActiveProcesses(tGID: String): Boolean = {
		var status = false
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val obj = iterator.next
				if (((obj VideoTaskGid) getOrElse "") equals tGID) {
					status = true
					break()
				}
			}
		}
		status
	}

	/**
	 * Attempt to restart unfinished downloads. The restart process only starts those unfinished tasks that
	 * are not in the ActiveProcesses.
	 */
	def restartDownload() {
		val arrUnfinishedTasks = DbControl queryActiveVideoTask()
		if ((arrUnfinishedTasks length) > 0) {
			for (t <- arrUnfinishedTasks) {
				if (!isTaskGIDInActiveProcesses(t.TaskGID.getOrElse(""))) {
					reprocessRequest(t.TaskInput.getOrElse(""), t.TaskOwner.getOrElse(""), t.TaskGID.getOrElse(""))
				}
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
				if ((e VideoTaskGid).orNull equals gid) {
					owner = Some((e Owner).orNull)
					break()
				}
			}
		}
		owner getOrElse ""
	}

	/**
	 * Kill the youtube-dl process ti
		DbControl updateVideoTask(taskGID, getOwner(taskGID), _inputTitle, status, totalFileLength, completedLength)ed to this download.
	 *
	 * @param gid unique ID of the download
	 */
	def killProcess(gid: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).orNull equals gid) {
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
				if ((e VideoTaskGid).orNull equals gid) {
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
				if ((e VideoTaskGid).orNull equals taskGID) {
					e VideoLastUpdated_= DateTime.now.getMillis
					break()
				}
			}
		}
	}

	/**
	 * Update the total length field of our download tracker object (Only if our new length is larger than the current one).
	 *
	 * @param taskGID unique ID of the download
	 * @param length total length of the download (in bytes)
	 */
	def updateTotalLength(taskGID: String, length: Long) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).orNull equals taskGID) {
					if ((e VideoTotalLength) < length) e.VideoTotalLength_:(length)
					break()
				}
			}
		}
	}

	/**
	 * Update the video extension of our download tracker object.
	 *
	 * @param taskGID unique ID of the download
	 * @param ext new video extension for our tracker object
	 */
	def updateVideoExtension(taskGID: String, ext: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).orNull equals taskGID) {
					e.VideoExt_:(ext)
					break()
				}
			}
		}
	}

	/**
	 * Update the video title field of our download tracker object.
	 *
	 * @param taskGID unique ID of the download
	 * @param title video title for our tracker object
	 */
	def updateVideoTitle(taskGID: String, title: String) {
		val iterator = ActiveProcesses iterator()
		breakable {
			while (iterator.hasNext) {
				val e = iterator.next
				if ((e VideoTaskGid).orNull equals taskGID) {
					e.VideoTitle_=(title)
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
			_executor.orNull
		}

		/**
		 * Starts the download by constructing the command line first and then starts the <code>DefaultExecutor</code>.
		 */
		override def run() {
			OUtils createUriFile(gid: String, url: String)
			// DEBUG
			LogWriter writeLog("Youtube-dl STARTING!", Level.DEBUG)
			// command line, e.g: youtube-dl --output="xxxfdf.%(ext)s" --write-info-json https://www.youtube.com/watch?v=TuZL0L4lZgo --print-traffic
			val sb = new StringBuilder
			sb append "youtube-dl" append " --output=" append "\"" append gid append ".%(ext)s\""
			sb append " --write-info-json " append url append " --print-traffic "
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
	 * Process the output result from <code>DefaultExecutor</code> into the log.
	 */
	class VOStream(gid: String) extends LogOutputStream {
		override def processLine(line: String, level: Int) {
			if (line startsWith "header: Content-Length:") {
				val xLength = (line replace("header: Content-Length:", "")).trim.toLong
				updateTotalLength(gid, xLength)
				LogWriter writeLog("YTDL Processor: Content-Length -> " + xLength + " bytes", Level.INFO)
			}
		}
	}

}

/**
 * A singleton object for VideoProcessor.
 */
object OVideoP extends VideoProcessor
