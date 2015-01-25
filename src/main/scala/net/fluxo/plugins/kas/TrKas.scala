package net.fluxo.plugins.kas

import net.xeoh.plugins.base.Plugin
import org.apache.log4j.Level

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.1, 25/01/15
 */
trait TrKas extends Plugin {

	def primaryCommand(): String
	def setMailLoggerName(name: String)
	def process(fullCommand: Array[String]): String
	def writeToLog(entry: String, logLevel: Level)

}
