/*
 * FluxoWS.scala
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

import javax.ws.rs.core.Application

/**
 * <code>javax.ws.rs.core.Application</code> object to be used by embedded Jetty server.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 11/04/14 2:34PM
 * @see javax.ws.rs.core.Application
 */
class FluxoWS extends Application {

	private val _services: java.util.Set[Object] = new java.util.HashSet[Object]

	/**
	 * Add a new <code>net.fluxo.dd.FluxoWSProcess</code> instance to the <code>Set</code>.
	 *
	 * @return a <code>java.util.Set</code>
	 */
	override def getSingletons: java.util.Set[Object] = {
		_services add new FluxoWSProcess
		_services
	}
}
