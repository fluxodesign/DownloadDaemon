/*
 * DownloadMonitor.scala
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

import org.apache.log4j.Level
import java.io.IOException
import org.quartz.impl.StdSchedulerFactory
import org.quartz._

/**
 * DownloadMonitor attempts to restart unfinished downloads and monitors the download progress every x
 * seconds interval.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 6/03/14
 * @see java.lang.Runnable
 */
class DownloadMonitor(dbMan: DbManager, parent: DaemonThread) extends Runnable {

	@volatile
	private var _isRunning: Boolean = true
	private val _scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler

	/**
	 * Start a <code>org.quartz.Scheduler</code> to run <code>UpdateProgressJob</code> and schedule it
	 * to run every 20 seconds.
	 */
	override def run() {
		//check for unfinished downloads and restart them...
		OAria restartDownloads()

		try {
			_scheduler start()

			val jobDetail: JobDetail = JobBuilder.newJob(classOf[UpdateProgressJob])
				.withIdentity("UpdateJob", "UpdateGroup")
				.build()

			val trigger: Trigger = TriggerBuilder.newTrigger().withIdentity("UpdateTrigger", "UpdateGroup")
				.startNow()
					.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(8).repeatForever())
				.build()
			_scheduler scheduleJob(jobDetail, trigger)

			val vidJobDetail: JobDetail = JobBuilder.newJob(classOf[VideoUpdateProgressJob])
					.withIdentity("VideoUpdateJob", "VideoUpdateGroup")
					.build()

			val vidTrigger: Trigger = TriggerBuilder.newTrigger().withIdentity("VideoUpdateTrigger", "VideoUpdateGroup")
					.startNow()
					.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(8).repeatForever())
					.build()
			_scheduler scheduleJob(vidJobDetail, vidTrigger)
		} catch {
			case se: SchedulerException =>
				LogWriter writeLog("Quartz Scheduler ERROR: " + se.getMessage + " caused by " + se.getUnderlyingException.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter.stackTraceToString(se), Level.ERROR)
			case ioe: IOException =>
				LogWriter writeLog("Quartz Scheduler IO/ERROR: " + ioe.getMessage + " caused by " + ioe.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
	}

	/**
	 * If anything in this class needs to be cleaned up, it needs to run from this method.
	 */
	def cleanup() {
		LogWriter.writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	/**
	 * Attempt to stop the process and the thread.
	 */
	def stop() {
		LogWriter.writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false
		_scheduler shutdown()
	}

}
