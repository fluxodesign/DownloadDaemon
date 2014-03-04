package net.fluxo.dd

import org.apache.log4j.{Logger, Level}
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import org.joda.time.DateTime

/**
 * User: Ronald Kurniawan (viper)
 * Date: 4/03/14
 * Time: 9:57 PM
 */
object LogWriter {

	def writeLog(message: String, level: Level) {
		val rootLogger = Logger.getRootLogger
		val mailLogger = Logger.getLogger("net.fluxo.MailLogger")
		val isMailAppenderActive = mailLogger.getLevel != Level.OFF
		level match {
			case Level.WARN =>
				rootLogger.warn(message)
				if (isMailAppenderActive) mailLogger.warn(message)
			case Level.TRACE =>
				rootLogger.trace(message)
				if (isMailAppenderActive) mailLogger.trace(message)
			case Level.INFO =>
				rootLogger.info(message)
				if (isMailAppenderActive) mailLogger.info(message)
			case Level.FATAL =>
				rootLogger.fatal(message)
				if (isMailAppenderActive) mailLogger.fatal(message)
			case Level.ERROR =>
				rootLogger.error(message)
				if (isMailAppenderActive) mailLogger.error(message)
			case Level.DEBUG =>
				rootLogger.debug(message)
				if (isMailAppenderActive) mailLogger.debug(message)
		}
	}

	def currentDateTime: String = {
		val formatter: DateTimeFormatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss ZZZZ")
		formatter.print(DateTime.now().getMillis)
	}
}
