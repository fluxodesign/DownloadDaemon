package net.fluxo.dd

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.{HandlerCollection, DefaultHandler, ResourceHandler}
import org.apache.log4j.Level
import org.eclipse.jetty.webapp.WebAppContext
import java.util.Properties
import org.eclipse.jetty.util.log.Logger
import org.eclipse.jetty.servlet.{ServletHolder, ServletContextHandler}
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher

/**
 * User: Ronald Kurniawan (viper)
 * Date: 12/03/14
 * Time: 11:10 AM
 */
class HttpDaemon(port: Int) extends Runnable {

	private val _server: Server = new Server(port)
	private var _isRunning: Boolean = true

	override def run() {
		setup()
	}

	def setup() {
		val wap = new WebAppContext()
		wap.setContextPath("/")
		wap.setWar(".")

		val webAppContext = new ServletContextHandler(ServletContextHandler SESSIONS)
		webAppContext setContextPath "/comm"
		webAppContext setInitParameter("resteasy.scan", "true")
		webAppContext setInitParameter("resteasy.servlet.mapping.prefix", "/rs")
		val webAppHolder = new ServletHolder(new HttpServletDispatcher)
		webAppHolder setInitOrder 1
		webAppHolder setInitParameter("javax.ws.rs.Application", "net.fluxo.dd.FluxoWS")
		webAppContext addServlet(webAppHolder, "/rs/*")

		val handlerCollection = new HandlerCollection()
		handlerCollection.setHandlers(Array(webAppContext, wap))

		_server.setHandler(handlerCollection)
		_server.setStopAtShutdown(true)

		try {
			_server.start()
			_server.join()
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error starting embedded jetty daemon", Level.ERROR)
				LogWriter.writeLog(e.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
	}

	def cleanup() {
		LogWriter.writeLog("DownloadMonitor thread is shut down!", Level.INFO)
	}

	def stop() {
		LogWriter.writeLog("Trying to stop DownloadMonitor thread before shutdown...", Level.INFO)
		_isRunning = false
		_server stop()
	}
}
