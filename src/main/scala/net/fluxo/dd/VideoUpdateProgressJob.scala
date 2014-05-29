/*
 * VideoUpdateProgressJob.scala
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

import org.quartz.{JobExecutionException, JobExecutionContext, Job}
import java.io.{File, IOException}
import org.apache.log4j.Level
import scala.util.control.Breaks._
import org.json.simple.JSONObject
import org.apache.commons.io.FileUtils

/**
 * This class monitors and updates the progress of youtube-dl downloads on database, so it can be queried by clients.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 29/05/14.
 * @see org.quartz.Job
 */
class VideoUpdateProgressJob extends Job {

	/**
	 * Monitor the progress of downloads and update the records in the database. It restarts any unfinished downloads, then
	 * checks for any active downloads for their progresses. Lastly, it checks for finished downloads , update their status
	 * in the database and move the files into target directory.
	 *
	 * @param context a <code>org.quartz.JobExecutionContext</code> object
	 * @throws org.quartz.JobExecutionException JobExecutionException
	 * @throws java.io.IOException IOException when dealing with files
	 */
	@throws(classOf[JobExecutionException])
	@throws(classOf[IOException])
	override def execute(context: JobExecutionContext) {
		OVideoP restartDownload()

		val videoIterator = (OVideoP ActiveProcesses) iterator()
		// DEBUG
		LogWriter writeLog("Starting VideoUpdateProgressJob, objects: ", Level.DEBUG)

		while (videoIterator.hasNext) {
			val vidObj = videoIterator.next
			val tGID = (vidObj VideoTaskGid) getOrElse null
			// DEBUG
			LogWriter writeLog("Video object, GID: " + tGID, Level.DEBUG)
			// if there's no extension defined in the video tracker object, try to obtain one from the json file
			val infoObject = new File(tGID + ".info.json")
			if (!((vidObj VideoExt) isDefined) && infoObject.exists) {
				val bestFormat = OUtils extractValueFromJSONFile(infoObject, "format_id")
				// DEBUG
				LogWriter writeLog("--> best format found: " + bestFormat, Level.DEBUG)
				val formatArray = OUtils extractArrayFromJSONObject(infoObject, "formats")
				val formatIterator = formatArray.iterator
				breakable {
					while (formatIterator.hasNext) {
						val f = formatIterator.next.asInstanceOf[JSONObject]
						if ((f get "format").asInstanceOf[String].equals(bestFormat)) {
							OVideoP updateVideoExtension(tGID, (f get "ext").asInstanceOf[String])
							// DEBUG
							LogWriter writeLog("--> extension for best format: " + (f get "ext").asInstanceOf[String], Level.DEBUG)
							break()
						}
					}
				}
			}
			if (!((vidObj VideoTitle) isDefined) && infoObject.exists) {
				val title = OUtils extractValueFromJSONFile(infoObject, "stitle")
				// DEBUG
				LogWriter writeLog("--> title: " + title, Level.DEBUG)
				OVideoP updateVideoTitle(tGID, title)
			}
			// look for the ".part" file
			val partFile = new File(tGID + "." + vidObj.VideoExt.getOrElse("") + ".part")
			val fullFile = new File(tGID + "." + vidObj.VideoExt.getOrElse(""))
			// if "part" file exists, calculate the download progress
			if (partFile.exists) {
				val downloaded = FileUtils sizeOf partFile
				val totalFileLength = vidObj.VideoTotalLength
				val fileName = ((vidObj VideoTitle) getOrElse "") + ((vidObj VideoExt) getOrElse "")
				DbControl updateVideoTask(tGID, OVideoP getOwner tGID, fileName, "active", totalFileLength, downloaded)
				// DEBUG
				LogWriter writeLog("--> PART file", Level.DEBUG)
				LogWriter writeLog("--> task GID: " + tGID, Level.DEBUG)
				LogWriter writeLog("--> downloaded: " + downloaded + " bytes", Level.DEBUG)
				LogWriter writeLog("--> total: " + totalFileLength + " bytes", Level.DEBUG)
				LogWriter writeLog("--> filename: " + fileName, Level.DEBUG)
			} else if (fullFile.exists) {
				// if full file exists, that means the download has finished. Rename and move the file to target dir, then cleanup
				val videoFile = new File(tGID + ((vidObj VideoExt) getOrElse ""))
				val targetVideoFile = new File(OUtils.readConfig.DownloadDir.getOrElse("") + "/" + ((vidObj VideoTitle) getOrElse "") +
						"." + ((vidObj VideoExt) getOrElse ""))
				FileUtils copyFile(videoFile, targetVideoFile)
				FileUtils forceDelete videoFile
				FileUtils forceDelete infoObject
				DbControl finishVideoTask(FileUtils.sizeOf(targetVideoFile), tGID)
				OVideoP removeFromList tGID
				// DEBUG
				LogWriter writeLog("--> DOWNLOAD FINISHED", Level.DEBUG)
				LogWriter writeLog("--> " + targetVideoFile.getAbsoluteFile + ": " + targetVideoFile.exists, Level.DEBUG)
			}
		}
	}
}
