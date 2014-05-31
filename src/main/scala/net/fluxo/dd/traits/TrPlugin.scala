package net.fluxo.dd.traits

import net.xeoh.plugins.base.Plugin
import org.apache.log4j.Level

/**
 * Created by viper 
 * Time: 31/05/14.
 */
trait TrPlugin extends Plugin {

	def primaryCommand(): String
	def setMailLoggerName(name: String)
	def process(fullCommand: Array[String]): String
	def writeToLog(entry: String, logLevel: Level)
}
