package net.fluxo.dd

import javax.ws.rs.core.Application

/**
 * User: Ronald Kurniawan (viper)
 * Date: 11/04/14 2:34PM
 * Comment:
 */

class FluxoWS extends Application {

	private val _services: java.util.Set[Object] = new java.util.HashSet[Object]

	override def getSingletons: java.util.Set[Object] = {
		_services add new FluxoWSProcess
		_services
	}
}
