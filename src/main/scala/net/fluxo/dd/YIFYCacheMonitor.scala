package net.fluxo.dd

import org.quartz.impl.StdSchedulerFactory
import org.quartz._
import org.apache.log4j.Level
import java.io.IOException

/**
 * User: Ronald Kurniawan (viper)
 * Date: 3/04/14 10:29PM
 * Comment:
 */
class YIFYCacheMonitor extends Runnable {

	@volatile
	private var _isRunning: Boolean = true
	private val _scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler

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

	def cleanup() {
		LogWriter.writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	def stop() {
		LogWriter.writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false
	}
}
