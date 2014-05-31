package net.fluxo.dd

import net.xeoh.plugins.base.PluginManager
import net.xeoh.plugins.base.impl.PluginManagerFactory
import java.io.File
import org.apache.log4j.Level

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 31/05/14.
 */
class PluginProcessor {

	private var _plugMan: Option[PluginManager] = None

	def getPluginManager: PluginManager = {
		if (!(_plugMan isDefined)) {
			val pm = PluginManagerFactory createPluginManager()
			// DEBUG
			LogWriter writeLog ("PluginManager: " + pm.toString, Level.DEBUG)
			_plugMan = Some(pm)
			val plugDir = new File("plugins/")
			// DEBUG
			LogWriter writeLog ("PlugDir exists? " + plugDir.exists(), Level.DEBUG)
			for (f <- plugDir.list()) {
				LogWriter writeLog("file: " + f, Level.DEBUG)
			}
			(_plugMan getOrElse null) addPluginsFrom(plugDir toURI)
		}
		_plugMan getOrElse null
	}

}

/**
 * A singleton object for PluginProcessor.
 */
object OPlugin extends PluginProcessor