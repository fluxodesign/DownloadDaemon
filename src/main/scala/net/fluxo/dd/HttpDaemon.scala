package net.fluxo.dd

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.{ContextHandlerCollection, ResourceHandler}
import org.apache.log4j.Level
import org.eclipse.jetty.util.resource.Resource

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
		val staticHandler = new ResourceHandler()
		val welcomeFiles: Array[String] = Array("favicon.ico", "index.html")
		staticHandler.setWelcomeFiles(welcomeFiles)
		staticHandler.setResourceBase(".")

		val contextHandlerCollections = new ContextHandlerCollection()
		contextHandlerCollections.setHandlers(Array(staticHandler))

		_server.setHandler(contextHandlerCollections)
		_server.setStopAtShutdown(true)

		try {
			_server.start()
			_server.join()
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error starting embedded jetty daemon", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
	}
}
