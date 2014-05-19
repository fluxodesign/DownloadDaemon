/*
 * LogWriter.scala
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

import org.apache.log4j.{Logger, Level}
import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import org.joda.time.DateTime
import java.io.{PrintWriter, StringWriter}

/**
 * Utility object for writing to application log file.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 4/03/14
 */
object LogWriter {

	/**
	 * Writes a message into the log file, and depending on the Level, should trigger a Mail response.
	 *
	 * @param message string to write into the log
	 * @param level <code>org.apache.log4j.Level</code> object
	 */
	def writeLog(message: String, level: Level) {
		val rootLogger = Logger.getRootLogger
		val mailLogger = Logger getLogger "net.fluxo.MailLogger"
		val isMailAppenderActive = mailLogger.getLevel != Level.OFF
		level match {
			case Level.WARN =>
				rootLogger warn message
				if (isMailAppenderActive) mailLogger warn message
			case Level.TRACE =>
				rootLogger trace message
				if (isMailAppenderActive) mailLogger trace message
			case Level.INFO =>
				rootLogger info message
				//if (isMailAppenderActive) mailLogger.info(message)
			case Level.FATAL =>
				rootLogger fatal message
				if (isMailAppenderActive) mailLogger fatal message
			case Level.ERROR =>
				rootLogger error message
				if (isMailAppenderActive) mailLogger error message
			case Level.DEBUG =>
				rootLogger debug message
				//if (isMailAppenderActive) mailLogger.debug(message)
		}
	}

	/**
	 * Returns a string representation of current date and time.
	 *
	 * @return a string of current date and time in the format of 'yyyy-MM-dd HH:mm:ss TZ'
	 */
	def currentDateTime: String = {
		val formatter: DateTimeFormatter = DateTimeFormat forPattern "yyyy-MM-dd HH:mm:ss ZZZZ"
		formatter print DateTime.now().getMillis
	}

	/**
	 * Turns an exception stack trace into a <code>String</code>.
	 *
	 * @param e Exception
	 * @return a string containing the stack trace of the Exception
	 */
	def stackTraceToString(e: Throwable): String = {
		val sw = new StringWriter
		val pw = new PrintWriter(sw)
		e printStackTrace pw
		sw.toString
	}
}
