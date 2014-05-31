package net.fluxo.dd

import net.xeoh.plugins.base.PluginManager
import net.xeoh.plugins.base.impl.PluginManagerFactory
import java.io.File

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 31/05/14.
 */
class PluginProcessor {

	private var _plugMan: Option[PluginManager] = None

	def getPluginManager: PluginManager = {
		if (!(_plugMan isDefined)) {
			_plugMan = Some(PluginManagerFactory createPluginManager())
			(_plugMan getOrElse null) addPluginsFrom(new File("plugins/") toURI)
		}
		_plugMan getOrElse null
	}

}

/**
 * A singleton object for PluginProcessor.
 */
object OPlugin extends PluginProcessor