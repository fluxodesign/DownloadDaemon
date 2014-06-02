/*
 * TrTPB.scala
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
package net.fluxo.plugins.tpb

import net.xeoh.plugins.base.Plugin
import org.apache.log4j.Level

/**
 * The trait (interface) that defines the methods used for processing TPB requests. This trait is directly
 * borrowed from the plugin.
 * <p>Part of the DownloadDaemon plugin framework.</p>
 *
 * @author Ronald Kurniawan (viper)
 * @version 30/05/14.
 */
trait TrTPB extends Plugin {

	def primaryCommand(): String
	def setMailLoggerName(name: String)
	def process(fullCommand: Array[String]): String
	def writeToLog(entry: String, logLevel: Level)
}
