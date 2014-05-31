package net.fluxo.dd

import net.xeoh.plugins.base.PluginManager
import net.xeoh.plugins.base.impl.PluginManagerFactory
import java.io.File
import net.xeoh.plugins.base.util.PluginManagerUtil

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 31/05/14.
 */
class PluginProcessor {

	private var _plugMan: Option[PluginManager] = None

	def getPluginManager: PluginManager = {
		if (!(_plugMan isDefined)) {
			val pm = PluginManagerFactory createPluginManager()
			_plugMan = Some(pm)
			val plugDir = new File("plugins/")
			(_plugMan getOrElse null) addPluginsFrom(plugDir toURI)
		}
		_plugMan getOrElse null
	}

	def getPluginManagerUtil: PluginManagerUtil = {
		val pmu = new PluginManagerUtil(getPluginManager)
		pmu
	}

}

/**
 * A singleton object for PluginProcessor.
 */
object OPlugin extends PluginProcessor