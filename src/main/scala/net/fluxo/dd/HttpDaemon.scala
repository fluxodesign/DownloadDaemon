package net.fluxo.dd

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.{HandlerCollection, DefaultHandler, ResourceHandler}
import org.apache.log4j.Level
import org.eclipse.jetty.webapp.WebAppContext
import java.util.Properties
import org.eclipse.jetty.util.log.Logger

/**
 * User: Ronald Kurniawan (viper)
 * Date: 12/03/14
 * Time: 11:10 AM
 */
class HttpDaemon(port: Int) extends Runnable {

	private val _server: Server = new Server(port)

	override def run() {
		setup()
	}

	def setup() {
		val wap = new WebAppContext()
		wap.setContextPath("/")
		wap.setWar(".")

		val handlerCollection = new HandlerCollection()
		handlerCollection.setHandlers(Array(wap))

		_server.setHandler(handlerCollection)
		_server.setStopAtShutdown(true)

		org.eclipse.jetty.util.log.Log.setLog(new NoLogging)

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

	class NoLogging extends Logger {
		override def getName: String = { "no" }
		override def warn(msg: String, args: Object*) { }
		override def warn(thrown: Throwable) { }
		override def warn(msg: String, thrown: Throwable) { }
		override def info(msg: String, args: Object*) { }
		override def info(thrown: Throwable) { }
		override def info(msg: String, thrown: Throwable) { }
		override def isDebugEnabled(): Boolean = { false }
		override def setDebugEnabled(enabled: Boolean) { }
		override def debug(msg: String, args: Object*) { }
		override def debug(thrown: Throwable) { }
		override def debug(msg: String, thrown: Throwable) { }
		override def getLogger(name: String): Logger = { this }
		override def ignore(ignored: Throwable) { }
	}
}
