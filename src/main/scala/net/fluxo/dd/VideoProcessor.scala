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
import net.fluxo.dd.dbo.VideoProcess
import org.joda.time.DateTime
import scala.util.control.Breaks._
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
			executor execute commandLine
		}
	}
}

/**
 * A singleton object for VideoProcessor.
 */
object OVideoP extends VideoProcessor
