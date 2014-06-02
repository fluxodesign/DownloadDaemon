/*
 * PluginProcessor.scala
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

import net.xeoh.plugins.base.PluginManager
import net.xeoh.plugins.base.impl.PluginManagerFactory
import java.io.File

/**
 * This is the entry point to access our plugins.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 31/05/14.
 */
class PluginProcessor {

	private var _plugMan: Option[PluginManager] = None

	/**
	 * Return our PluginManager (creating it from the factory if necessary, and load all
	 * the plugins from the "plugins/" directory.
	 *
	 * @return <code>net.xeoh.plugins.base.PluginManager</code>
	 */
	def getPluginManager: PluginManager = {
		if (!(_plugMan isDefined)) {
			val pm = PluginManagerFactory createPluginManager()
			_plugMan = Some(pm)
			val plugDir = new File("plugins/")
			(_plugMan getOrElse null) addPluginsFrom(plugDir toURI)
		}
		_plugMan getOrElse null
	}

}

/**
 * A singleton object for PluginProcessor.
 */
object OPlugin extends PluginProcessor