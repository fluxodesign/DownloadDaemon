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

import org.apache.log4j.Level
import org.eclipse.jetty.server._
import org.eclipse.jetty.server.handler.{DefaultHandler, HandlerCollection}
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.util.ssl.SslContextFactory
import org.eclipse.jetty.webapp.WebAppContext
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
class HttpDaemon(port: Int, sslPort: Int) extends Runnable {

	private val _server: Server = new Server()
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

		val defaultHandler = new DefaultHandler

		val webAppContext = new ServletContextHandler(ServletContextHandler SESSIONS)
		webAppContext setContextPath "/comm"
		webAppContext setInitParameter("resteasy.scan", "true")
		webAppContext setInitParameter("resteasy.servlet.mapping.prefix", "/rs")
		val webAppHolder = new ServletHolder(new HttpServletDispatcher)
		webAppHolder setInitOrder 1
		webAppHolder setInitParameter("javax.ws.rs.Application", "net.fluxo.dd.FluxoWS")
		webAppContext addServlet(webAppHolder, "/rs/*")

		val handlerCollection = new HandlerCollection()
		handlerCollection setHandlers Array(webAppContext, wap, defaultHandler)

		_server setHandler handlerCollection
		_server setStopAtShutdown true

		val sslContextFactory = new SslContextFactory((OUtils readConfig).SSLKeystore.getOrElse(""))
		sslContextFactory setKeyStorePassword (OUtils readConfig).SSLKeystorePassword.getOrElse("")
		sslContextFactory setKeyManagerPassword (OUtils readConfig).SSLKeymanagerPassword.getOrElse("")
		sslContextFactory addExcludeProtocols "SSLv3"
		sslContextFactory addExcludeProtocols "SSLv2Hello"
		sslContextFactory setIncludeCipherSuites("TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
			"SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
			"TLS_RSA_WITH_AES_128_CBC_SHA",
			"SSL_RSA_WITH_3DES_EDE_CBC_SHA",
			"TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
			"SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA")
		val httpsConfig = new HttpConfiguration()
		httpsConfig setSecureScheme "https"
		httpsConfig setSecurePort sslPort
		httpsConfig setOutputBufferSize 32768
		httpsConfig addCustomizer new SecureRequestCustomizer()
		val https = new ServerConnector(_server, new SslConnectionFactory(sslContextFactory, "http/1.1"),
			new HttpConnectionFactory(httpsConfig))
		https setPort sslPort
		https setIdleTimeout 60000

		val httpConfig = new HttpConfiguration()
		httpConfig setOutputBufferSize 32768
		val http = new ServerConnector(_server)
		http setPort port
		http setIdleTimeout 30000

		_server setConnectors Array(https, http)

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
