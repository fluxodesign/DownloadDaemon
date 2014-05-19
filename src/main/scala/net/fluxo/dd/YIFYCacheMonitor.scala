/*
 * YIFYCacheMonitor.scala
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

import org.quartz.impl.StdSchedulerFactory
import org.quartz._
import org.apache.log4j.Level
import java.io.IOException

/**
 * This class starts and schedule a new Quartz job to check the site for new items every X hours (in this case, it is
 * hard coded to 6 hours).
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 3/04/14
 * @see java.lang.Runnable
 */
class YIFYCacheMonitor extends Runnable {

	@volatile
	private var _isRunning: Boolean = true
	private val _scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler

	/**
	 * Starts a <code>org.quartz.Scheduler</code> to run <code>YIFYCacheJob</code> and schedule it
	 * to run every 6 hours.
	 */
	override def run() {
		try {
			_scheduler start()
			val jobDetail = JobBuilder.newJob(classOf[YIFYCacheJob])
				.withIdentity("YIFYCache", "YIFYGroup")
				.build()
			val trigger = TriggerBuilder.newTrigger().withIdentity("YIFYTrigger", "YIFYGroup")
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInHours(6).repeatForever())
				.build()
			_scheduler scheduleJob(jobDetail, trigger)
		} catch {
			case se: SchedulerException =>
				LogWriter writeLog("Quartz Scheduler ERROR: " + (se getMessage), Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString se, Level.ERROR)
			case ioe: IOException =>
				LogWriter writeLog("Quartz Scheduler IO/ERROR: " + (ioe getMessage), Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ioe, Level.ERROR)
		}
	}

	/**
	 * If anything in this class needs to be cleaned up, it needs to run from this method.
	 */
	def cleanup() {
		LogWriter writeLog("YIFYCacheMonitor thread is shut down!", Level.INFO)
	}

	/**
	 * Attempt to stop the process and the thread.
	 */
	def stop() {
		LogWriter writeLog("Trying to stop YIFYCacheMonitor thread before shutdown...", Level.INFO)
		_isRunning = false
		_scheduler shutdown()
	}
}
