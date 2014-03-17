package net.fluxo.dd

import org.apache.log4j.Level
import java.io.{IOException, FileInputStream, File}
import org.apache.commons.io.FileUtils
import org.quartz.impl.StdSchedulerFactory
import org.quartz._
import org.apache.xmlrpc.client.{XmlRpcClient, XmlRpcClientConfigImpl}
import java.net.URL
import java.util
import net.fluxo.dd.dbo.Task
import org.joda.time.DateTime
import java.util.Properties
import org.apache.xmlrpc.serializer.{TypeSerializer, StringSerializer}
import org.xml.sax.{ContentHandler, SAXException}
import org.apache.xmlrpc.common.{XmlRpcStreamConfig, TypeFactoryImpl, XmlRpcController}

/**
 * User: Ronald Kurniawan (viper)
 * Date: 6/03/14
 * Time: 10:09 PM
 */
class DownloadMonitor(dbMan: DbManager, parent: DaemonThread) extends Runnable {

	@volatile
	private var _isRunning: Boolean = true
	private val _scheduler: Scheduler = StdSchedulerFactory.getDefaultScheduler

	override def run() {
		//check for unfinished downloads and restart them...
		OAria.restartDownloads()

		try {
			_scheduler.start()

			val jobDetail: JobDetail = JobBuilder.newJob(classOf[UpdateProgressJob])
				.withIdentity("UpdateJob", "UpdateGroup")
				.build()

			val trigger: Trigger = TriggerBuilder.newTrigger().withIdentity("UpdateTrigger", "UpdateGroup")
				.startNow()
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(20).repeatForever())
				.build()
			_scheduler.scheduleJob(jobDetail, trigger)
		} catch {
			case se: SchedulerException =>
				LogWriter.writeLog("Quartz Scheduler ERROR: " + se.getMessage + " caused by " + se.getUnderlyingException.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(se), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("Quartz Scheduler IO/ERROR: " + ioe.getMessage + " caused by " + ioe.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
	}

	def cleanup() {
		LogWriter.writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	def stop() {
		LogWriter.writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false

	}

	def secondToMillis(secs: Long): Long = {
		secs * 1000
	}

}
