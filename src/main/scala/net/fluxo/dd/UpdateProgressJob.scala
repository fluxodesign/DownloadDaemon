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

import java.io._
import java.util

import org.apache.commons.io.FileUtils
import org.apache.log4j.Level
import org.apache.xmlrpc.XmlRpcException
import org.quartz.{Job, JobExecutionContext, JobExecutionException}

import scala.util.control.Breaks._

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
	override def execute(context: JobExecutionContext) {
		try {
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
						val tasks = DbControl queryTask a.AriaTaskGid.orNull
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
								} else {
									DbControl updateTaskTailGID(tasks(0).TaskGID.getOrElse(""), tasks(0).TaskGID.getOrElse(""))
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
                            // DEBUG
                            val jMapIterator = (jMap keySet()) iterator()
                            while (jMapIterator.hasNext) {
                                val key = jMapIterator.next
                                LogWriter writeLog("--> activeTask: [" + key + "]: " + (jMap get key).toString, Level.DEBUG)
                            }
							if (jMap != null) {
								val tailGID = (OUtils extractValueFromHashMap(jMap, "gid")).toString
                                // DEBUG
                                LogWriter writeLog("---> gid: " + tailGID, Level.DEBUG)
								val task = {
									if (tailGID.length > 0) DbControl queryTaskTailGID tailGID else null
								}
								task.TaskTailGID_=(tailGID)
								val cl = (OUtils extractValueFromHashMap(jMap, "completedLength")).toString.toLong
                                // DEBUG
                                LogWriter writeLog ("---> completedLength: " + cl, Level.DEBUG)
								task.TaskCompletedLength_=(cl)
								val tl = (OUtils extractValueFromHashMap(jMap, "totalLength")).toString.toLong
                                // DEBUG
                                LogWriter writeLog ("---> totalLength: " + tl, Level.DEBUG)
								task.TaskTotalLength_=(tl)
								task.TaskStatus_=(OUtils.extractValueFromHashMap(jMap, "status").toString)
                                // DEBUG
                                LogWriter writeLog ("---> status: " + Some(task.TaskStatus), Level.DEBUG)
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
                                        // DEBUG
                                        LogWriter writeLog("---> package: " + Some(task.TaskPackage), Level.DEBUG)
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
                            // DEBUG
                            val jMapIterator = (jMap keySet()) iterator()
                            while (jMapIterator hasNext) {
                                val key = jMapIterator.next
                                LogWriter writeLog("--> finished task: [" + key + "]: " + (jMap get key).toString, Level.DEBUG)
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
								// The old approach is to query for a count of object(s) in the DB with 'GID',
								// 'infoHash' and 'totalLength' matching this particular torrent...
								val qf = DbControl queryFinishTask(gid, infoHash, tl)
								if (qf.CPCount > 0) {
									DbControl finishTask(status, cl, gid, infoHash, tl)
									flagCompleted = true
									// move the package to a directory specified in config...
									if (OUtils.readConfig.DownloadDir.orNull.length > 0) {
										if (a.AriaHttpDownload) {
											// HTTP downloads usually are just for 1 file...
											val packageFile = new File(qf.CPPackage.orNull)
											val destDir = new File(OUtils.readConfig.DownloadDir.getOrElse(""))
											if (packageFile.isFile && packageFile.exists() && destDir.isDirectory && destDir.exists()) {
												FileUtils moveFileToDirectory(packageFile, destDir, false)
											} else LogWriter writeLog("Failed to move file " + qf.CPPackage.getOrElse("{empty file}") +
													" to " + OUtils.readConfig.DownloadDir.getOrElse("{empty target dir}"), Level.INFO)
										} else {
											val packageDir = new File(qf.CPPackage.orNull)
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
