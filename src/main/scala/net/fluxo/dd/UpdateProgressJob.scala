/*
 * UpdateProgressJob.scala
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

import org.quartz.{Job, JobExecutionContext, JobExecutionException}
import org.apache.log4j.Level
import java.io._
import java.util
import org.apache.commons.io.FileUtils
import org.apache.xmlrpc.XmlRpcException
import scala.util.control.Breaks._
import org.json.simple.JSONObject

/**
 * UpdateProgressJob represents a <code>Job</code> where it monitors the active and finished downloads and updates the
 * progress to the database so it can be queried by clients.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 15/03/14
 * @see org.quartz.Job
 */
class UpdateProgressJob extends Job {

	private var _currentPort: Int = 0

	/**
	 * Monitor the progress of downloads and update the records in the database. It restarts any unfinished downloads, then
	 * checks for any active downloads for their progresses. Lastly, it checks for finished downloads and shut downs their
	 * process, update their status in the database and move the files into target directory.
	 *
	 * @param context a <code>org.quartz.JobExecutionContext</code> object
	 * @throws org.quartz.JobExecutionException JobExecutionException
	 */
	@throws(classOf[JobExecutionException])
	@throws(classOf[IOException])
	override def execute(context: JobExecutionContext) {
		try {
			OVideoP restartDownload()

			val videoIterator = (OVideoP ActiveProcesses) iterator()

			while (videoIterator.hasNext) {
				val vidObj = videoIterator.next
				val tGID = (vidObj VideoTaskGid) getOrElse null
				// if there's no extension defined in the video tracker object, try to obtain one from the json file
				val infoObject = new File(tGID + ".info.json")
				if (!((vidObj VideoExt) isDefined) && infoObject.exists) {
					val bestFormat = OUtils extractValueFromJSONFile(infoObject, "format_id")
					val formatArray = OUtils extractArrayFromJSONObject(infoObject, "formats")
					val formatIterator = formatArray.iterator
					breakable {
						while (formatIterator.hasNext) {
							val f = formatIterator.next.asInstanceOf[JSONObject]
							if ((f get "format").asInstanceOf[String].equals(bestFormat)) {
								OVideoP updateVideoExtension(tGID, (f get "ext").asInstanceOf[String])
								break()
							}
						}
					}
				}
				if (!((vidObj VideoTitle) isDefined) && infoObject.exists) {
					val title = OUtils extractValueFromJSONFile(infoObject, "stitle")
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
				}
			}

			if (OUtils.allPortsFree) OAria restartDownloads()

			val iterator = OAria.ActiveProcesses.iterator()
			while (iterator.hasNext) {
				breakable {

					try {

						var flagCompleted: Boolean = false
						val a = iterator.next
						_currentPort = a.AriaPort
						// get an RPC client for a particular port...
						val client = OUtils getXmlRpcClient a.AriaPort
						// we need to acquire the TAIL GID if this is a new download, or a restart...
						val tasks = DbControl queryTask a.AriaTaskGid.getOrElse(null)
						if (tasks.length > 0 && !tasks(0).IsTaskCompleted) {
							val ts = OUtils sendAriaTellStatus(tasks(0).TaskGID.getOrElse(""), client)
							val jmap = ts.asInstanceOf[util.HashMap[String, Object]]
							if (!a.AriaHttpDownload) {
								val tg = {
									try {
										(OUtils extractValueFromHashMap(jmap, "followedBy")).asInstanceOf[Array[Object]]
									} catch {
										case e: Exception => null
									}
								}
								if (tg != null && tg.length > 0 && tg(0) != null) {
									DbControl updateTaskTailGID(tasks(0).TaskGID.getOrElse(""), tg(0).asInstanceOf[String])
								}
							}
						}

						val activeTasks = OUtils sendAriaTellActive client
						for (o <- activeTasks) {
							val jMap = {
								var hm: util.HashMap[String, Object] = null
								try {
									hm = o.asInstanceOf[util.HashMap[String, Object]]
								} catch {
									case e: Exception =>
										LogWriter writeLog("Port " + _currentPort + "/ActiveTask: " + e.getMessage, Level.INFO)
								}
								hm
							}
							if (jMap != null) {
								val tailGID = (OUtils extractValueFromHashMap(jMap, "gid")).toString
								val task = {
									if (tailGID.length > 0) DbControl queryTaskTailGID tailGID else null
								}
								val cl = (OUtils extractValueFromHashMap(jMap, "completedLength")).toString.toLong
								task.TaskCompletedLength_=(cl)
								val tl = (OUtils extractValueFromHashMap(jMap, "totalLength")).toString.toLong
								task.TaskTotalLength_=(tl)
								task.TaskStatus_=(OUtils.extractValueFromHashMap(jMap, "status").toString)
								task.TaskInfoHash_=({
									if (task.TaskIsHttp) "noinfohash"
									else (OUtils extractValueFromHashMap(jMap, "infoHash")).toString
								})
								// now we extract the 'PACKAGE' name, which basically is the name of the directory of the downloaded files...
								if (!a.AriaHttpDownload) {
									val btDetailsMap = {
										try {
											OUtils.extractValueFromHashMap(jMap, "bittorrent").asInstanceOf[java.util.HashMap[String, Object]]
										} catch {
											case e: Exception => null
										}
									}
									val infoMap = {
										if (btDetailsMap != null) {
											try {
												OUtils.extractValueFromHashMap(btDetailsMap, "info").asInstanceOf[java.util.HashMap[String, Object]]
											}
										} else null
									}
									if (infoMap != null) {
										task.TaskPackage_=(OUtils.extractValueFromHashMap(infoMap, "name").toString)
									}
								}
								if (task.TaskGID.getOrElse("").length > 0) DbControl.updateTask(task)
							}
						}

						val finishedTasks = OUtils sendAriaTellStopped client
						for (o <- finishedTasks) {
							val jMap = {
								var hm: util.HashMap[String, Object] = null
								try {
									hm = o.asInstanceOf[util.HashMap[String, Object]]
								} catch {
									case e: Exception =>
										LogWriter writeLog("Port " + _currentPort + "/FinishedTask: " + e.getMessage, Level.INFO)
								}
								hm
							}
							if (jMap != null) {
								val status = OUtils.extractValueFromHashMap(jMap, "status").toString
								val gid = OUtils.extractValueFromHashMap(jMap, "gid").toString
								val infoHash = {
									val tasks = DbControl.queryTask(gid)
									if (tasks.length > 0 && tasks(0).TaskIsHttp) "noinfohash"
									else OUtils.extractValueFromHashMap(jMap, "infoHash").toString
								}
								val cl = OUtils.extractValueFromHashMap(jMap, "completedLength").toString.toLong
								val tl = OUtils.extractValueFromHashMap(jMap, "totalLength").toString.toLong
								val qf = DbControl queryFinishTask(gid, infoHash, tl)
								if (qf.CPCount > 0) {
									DbControl finishTask(status, cl, gid, infoHash, tl)
									flagCompleted = true
									// move the package to a directory specified in config...
									if (OUtils.readConfig.DownloadDir.getOrElse(null).length > 0) {
										if (a.AriaHttpDownload) {
											// HTTP downloads usually are just for 1 file...
											val packageFile = new File(qf.CPPackage.getOrElse(null))
											val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse(""))
											if (packageFile.isFile && packageFile.exists() && destDir.isDirectory && destDir.exists()) {
												FileUtils moveFileToDirectory(packageFile, destDir, false)
											} else LogWriter writeLog("Failed to move file " + qf.CPPackage.getOrElse("{empty file}") +
													" to " + OUtils.readConfig.DownloadDir.getOrElse("{empty target dir}"), Level.INFO)
										} else {
											val packageDir = new File(qf.CPPackage.getOrElse(null))
											val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse("") + "/" + qf.CPPackage.getOrElse(""))
											if (packageDir.isDirectory && packageDir.exists && !destDir.exists) {
												FileUtils moveDirectory(packageDir, destDir)
											} else if (packageDir.isFile && packageDir.exists && !destDir.exists) {
												FileUtils moveFile(packageDir, destDir)
											} else LogWriter writeLog("directory " + destDir.getAbsolutePath + " exist!", Level.INFO)
										}
									}
								}
							}
						}

						// shutdown this aria2 process when it's update is finished...
						if (activeTasks.length == 0 && flagCompleted) {
							OUtils sendAriaTellShutdown client
							iterator remove()
						}

					} catch {
						case xe: XmlRpcException =>
							LogWriter writeLog("Port " + _currentPort + ": " + xe.getMessage, Level.ERROR)
							// if a download is hanging or call to XML-RPC server returns an error,
							// we need to shut down the offending thread and restart the download...
							LogWriter writeLog("Shutting down the offending thread...", Level.INFO)
							OAria killProcess _currentPort
					}


				}
			}
		} catch {
			case ie: InterruptedException =>
				LogWriter writeLog(ie.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter.stackTraceToString(ie), Level.ERROR)
		}
	}

}
