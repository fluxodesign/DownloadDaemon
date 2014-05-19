/*
 * HttpDaemon.scala
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

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerCollection
import org.apache.log4j.Level
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher

/**
 * HttpDaemon is one of the daemon processes managed by `DaemonThread` class.
 * It starts an embedded Jetty server session on the specified port from configuration file.
 *
 * @param port the port number where embedded Jetty daemon should be bound to.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 12/03/14
 * @see java.lang.Runnable
 */
class HttpDaemon(port: Int) extends Runnable {

	private val _server: Server = new Server(port)
	private var _isRunning: Boolean = true

	/**
	 * Calls the `setup()` method.
	 * @see java.lang.Runnable#run()
	 */
	override def run() {
		setup()
	}

	/**
	 * Initiate a new Jetty's `org.eclipse.jetty.webapp.WebAppContext` to serve REST methods.
	 * <p>The REST methods should be accessible from http://[address-or-ip]:[port]/comm/rs/ws/[method]
	 */
	def setup() {
		val wap = new WebAppContext()
		wap setContextPath "/"
		wap setWar "."

		val webAppContext = new ServletContextHandler(ServletContextHandler SESSIONS)
		webAppContext setContextPath "/comm"
		webAppContext setInitParameter("resteasy.scan", "true")
		webAppContext setInitParameter("resteasy.servlet.mapping.prefix", "/rs")
		val webAppHolder = new ServletHolder(new HttpServletDispatcher)
		webAppHolder setInitOrder 1
		webAppHolder setInitParameter("javax.ws.rs.Application", "net.fluxo.dd.FluxoWS")
		webAppContext addServlet(webAppHolder, "/rs/*")

		val handlerCollection = new HandlerCollection()
		handlerCollection setHandlers Array(webAppContext, wap)

		_server setHandler handlerCollection
		_server setStopAtShutdown true

		try {
			_server start()
			_server join()
		} catch {
			case e: Exception =>
				LogWriter writeLog("Error starting embedded jetty daemon", Level.ERROR)
				LogWriter writeLog(e.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
		}
	}

	/**
	 * All resource clean up methods should be called from inside this method.
	 */
	def cleanup() {
		LogWriter writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	/**
	 * Stop the embedded Jetty server session.
	 */
	def stop() {
		LogWriter writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false
		_server stop()
	}
}
