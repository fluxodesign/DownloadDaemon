/*
 * XMPPMonitor.scala
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

import org.jivesoftware.smack._
import org.apache.log4j.Level
import org.joda.time.DateTime
import org.jivesoftware.smack.packet.{Presence, Message}
import net.fluxo.dd.dbo.Task
import net.fluxo.dd.traits.TrTPB
import org.apache.commons.validator.routines.IntegerValidator
import java.io.{File, InputStreamReader, BufferedReader}
import org.apache.commons.codec.net.URLCodec
import java.util
import net.xeoh.plugins.base.options.getplugin.OptionCapabilities

/**
 * XMPPMonitor manages XMPP connection to Facebook or GMail's chat. This enables user(s) to issue commands directly via
 * XMPP chat client to the server and receive feedback directly from server. Only one connection can be active at any one
 * time. Facebook is the preferred XMPP provider, because of the issue (or rumour) that Google will retire its XMPP server.
 * <p>User/administrator must provide an account on Facebook / GMail for this daemon to use.
 *
 * @param xmppProvider the XMPP provider; at this time it is limited to "google" or "facebook"
 * @param xmppServer the URL of XMPP provider
 * @param xmppPort port number of XMPP provider
 * @param xmppAccount user ID for the server to log in into the XMPP provider
 * @param xmppPassword password for the server to log in into the XMPP provider
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 7/03/14
 * @see java.lang.Runnable
 *
 */
class XMPPMonitor(xmppProvider: String, xmppServer: String, xmppPort: Int, xmppAccount: String, xmppPassword: String, parent: DaemonThread) extends Runnable {

	@volatile
	private var _isRunning = true
	private var _smackConfig: ConnectionConfiguration = _
	private var _xmppc: XMPPConnection = _
	private var _xmppConnected: Boolean = false
	private var _xmppAuthenticated: Boolean = false
	private var _externalIP: Option[String] = Some("127.0.0.1")

	/**
	 * Set up all the configurations before connecting to XMPP provider.
	 */
	def setup() {
		_smackConfig = {
			if (xmppProvider equals "google") new ConnectionConfiguration(xmppServer, xmppPort, "gmail.com")
			else new ConnectionConfiguration(xmppServer, xmppPort, xmppServer)
		}
		_smackConfig setReconnectionAllowed true
		_smackConfig setSendPresence true
		_smackConfig setSASLAuthenticationEnabled true
		_smackConfig setRosterLoadedAtLogin true
		_smackConfig setSecurityMode ConnectionConfiguration.SecurityMode.enabled
		new Thread(new WgetExternalIP) start()
	}

	/**
	 * Attempt to connect and authenticate to the XMPP provider.
	 *
	 * @return true if our daemon is connected and authenticated to the XMPP provider and ready to chat; false otherwise,
	 *         or if we get any errors
	 */
	def connect(): Boolean = {
		_xmppc = new XMPPConnection(_smackConfig)
		LogWriter writeLog("Connecting to chat server '" + xmppServer + "'...", Level.INFO)
		val currentTime: Long = DateTime.now.getMillis
		while (!_xmppConnected && withinMinuteRange(currentTime, 2)) {
			try {
				SASLAuthentication supportSASLMechanism("PLAIN", 0)
				_xmppc connect()
				if (_xmppc.isConnected) {
					_xmppConnected = true
					LogWriter writeLog("Connected to chat server", Level.INFO)
				} else {
					try {
						Thread sleep 1000
					} catch {
						case ie: InterruptedException => ie printStackTrace()
					}
				}
			} catch {
				case xmppEx: XMPPException =>
					LogWriter writeLog("Failed to connect to chat server", Level.ERROR)
					LogWriter writeLog(xmppEx.getMessage, Level.ERROR)
					if ((xmppEx getXMPPError) != null) {
						LogWriter.writeLog("XMPP ERROR: " + xmppEx.getXMPPError.getMessage, Level.ERROR)
					}
					LogWriter writeLog(LogWriter stackTraceToString xmppEx, Level.ERROR)
				case ex: Exception =>
					LogWriter writeLog("Failed to connect to chat server", Level.ERROR)
					LogWriter writeLog(ex.getMessage, Level.ERROR)
					LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
			}
		}
		if (!_xmppConnected) {
			LogWriter writeLog("Failed to connect to chat server", Level.ERROR)
			return false
		}
		var retries = 5
		while (!_xmppAuthenticated && retries > 0) {
			try {
				_xmppc login(xmppAccount, xmppPassword)
				_xmppAuthenticated = _xmppc.isAuthenticated
				LogWriter writeLog("XMPP Credentials authenticated", Level.INFO)
				setupListener()
				val presence: Presence = new Presence(Presence.Type.available)
				_xmppc sendPacket presence
			} catch {
				case xmppEx: XMPPException =>
					LogWriter writeLog("Failed to authenticate to XMPP server", Level.ERROR)
					LogWriter writeLog(xmppEx.getMessage, Level.ERROR)
					if (xmppEx.getXMPPError != null) {
						LogWriter writeLog("XMPP ERROR: " + xmppEx.getXMPPError.getMessage, Level.ERROR)
					}
					LogWriter writeLog(LogWriter.stackTraceToString(xmppEx), Level.ERROR)
					retries -= 1
			}
		}
		_xmppc.isAuthenticated
	}

	/**
	 * Set up a listener for XMPP chat
	 */
	def setupListener() {
		if (_xmppAuthenticated) {
			_xmppc.getChatManager.addChatListener(new XMPPChatListener)
		}
	}

	/**
	 * Check if daemon is connected to XMPP provider.
	 *
	 * @return if if daemon is connected; false otherwise
	 */
	def isXmppConnected: Boolean = _xmppConnected

	/**
	 * Check if daemon is authenticated to XMPP provider.
	 *
	 * @return true if daemon is authenticated; false otherwise
	 */
	def isXmppAuthenticated: Boolean = _xmppAuthenticated

	/**
	 * Check whether this very moment is within <code>range</code> minutes from <code>startPoint</code>.
	 *
	 * @param startPoint the millis of the time to start measuring
	 * @param range how long after <code>startPoint</code>?
	 * @return true if now is within <code>range</code> from <code>startPoint</code>
	 */
	def withinMinuteRange(startPoint: Long, range: Int): Boolean = {
		val current: Long = DateTime.now().getMillis
		val startTime: DateTime = new DateTime(startPoint)
		startTime.plusMinutes(range).getMillis >= current
	}

	/**
	 * Return millis from seconds.
	 *
	 * @param secs seconds to convert to milliseconds
	 * @return milliseconds
	 */
	def secondsToMillis(secs: Long): Long = {
		secs * 1000
	}

	/**
	 * Attempt to recognise the operating system family of the local system.
	 *
	 * @return string representing the operating system family
	 */
	def getOpSys: String = {
		val rawOS = System.getProperty("os.name").toLowerCase
		var retVal = rawOS
		if ((rawOS indexOf "win") >= 0) {
			retVal = "windows"
		} else if ((rawOS indexOf "mac") >= 0) {
			retVal = "mac"
		} else if ((rawOS indexOf "nix") >= 0 || (rawOS indexOf "aix") >= 0) {
			retVal = "unix"
		} else if ((rawOS indexOf "nux") >= 0) {
			retVal = "linux"
		} else if ((rawOS indexOf "sunos") >= 0 || (rawOS indexOf "solaris") >= 0) {
			retVal = "solaris"
		}
		retVal
	}

	/**
	 * Ping the server, see if it is reachable.
	 *
	 * @param url URL to ping
	 * @return true if server is reachable; false otherwise
	 */
	def isServerReachable(url: String): Boolean = {
		var switch: String = ""
		getOpSys match {
			case "windows" => switch = "-n"
			case _ => switch = "-c"
		}
		val pb = new ProcessBuilder("ping", switch, "1", url)
		val proc = pb start()

		val returnVal = proc waitFor()
		returnVal == 0
	}

	private var _isXmppServerReachable: Boolean = false

	/**
	 * This is the meat of the XMPPMonitor. Based on the flag values, we can examine what state our attempt is at the moment
	 * (contacting, authenticating, etc..). Because it is continually running, every time the connection is broken, it is
	 * always try to reconnect.
	 */
	override def run() {
		setup()
		var whatToDo = "tryReachServer"
		while (_isRunning) whatToDo match {
			case "tryReachServer" =>
				var retries: Int = 0
				LogWriter writeLog("Contacting chat server", Level.INFO)
				try {
					while (!_isXmppServerReachable) {
						_isXmppServerReachable = isServerReachable(xmppServer)
						if (!_isXmppServerReachable) {
							if (retries >= 20) {
								LogWriter writeLog("XMPP Server is not contactable after 20 retries. Retrying in 5 minutes",
										Level.INFO)
								Thread interrupted()
								Thread sleep secondsToMillis(5 * 60)
								retries = 0
							} else {
								LogWriter writeLog("XMPP Server not responding. Retrying in 20 seconds...", Level.INFO)
								Thread interrupted()
								Thread sleep secondsToMillis(20)
								retries += 1
							}
						}
					}
				} catch {
					case ie: InterruptedException =>
						if (!_isRunning) {
							cleanup()
						}
				}
				if (_isXmppServerReachable) {
					whatToDo = "tryConnectServer"
				}
			case "tryConnectServer" =>
				if (!isServerReachable(xmppServer)) {
					whatToDo = "tryReachServer"
					_isXmppServerReachable = false
				} else {
					LogWriter writeLog("Connecting to XMPP Server...", Level.INFO)
					var retries: Int = 0
					var connected = false
					// We are going to establish connection here; If failing to connect, we will wait for 20 seconds
					// before retrying to connect again; We will try 20 times before resting for 5 minutes...
					while (!connected) try {
						connected = connect()
						if (!connected) {
							if (retries >= 20) {
								LogWriter writeLog("Connecting to XMPP server '" + xmppServer +
										"' has been unsuccessful after 20 retries. Retrying in 5 minutes...", Level.INFO)
								Thread interrupted()
								Thread sleep secondsToMillis(5 * 60)
								retries = 0
							} else {
								LogWriter writeLog("Connecting to XMPP server '" + xmppServer +
										"' failed. Retrying in 10 seconds...", Level.INFO)
								Thread interrupted()
								Thread sleep secondsToMillis(10)
								retries += 1
							}
						}
					} catch {
						case ie: InterruptedException =>
							if (!_isRunning) {
								cleanup()
							}
					}
					if (connected) {
						whatToDo = "sentryTask"
					}
				}
			case "sentryTask" =>
				if (!isServerReachable(xmppServer) && !_xmppc.isConnected) {
					whatToDo = "tryReachServer"
					_isXmppServerReachable = false
				} else if (!_xmppc.isConnected || !_xmppc.isAuthenticated) {
					_xmppc disconnect()
					whatToDo = "tryConnectServer"
				} else {
					try {
						Thread interrupted()
						Thread sleep secondsToMillis(10)
					} catch {
						case ie: InterruptedException =>
							if (!_isRunning) {
								cleanup()
							}
					}
				}
		}
	}

	/**
	 * Every time a shutdown is initiated, all clean up routines should be inside this method.
	 */
	def cleanup() {
		if (_xmppc != null) {
			if (_xmppc.isConnected) _xmppc.disconnect()
		}
		LogWriter writeLog("XMPP Monitoring thread shut down!", Level.INFO)
	}

	/**
	 * Stop the XMPP monitoring thread.
	 */
	def stop() {
		LogWriter writeLog("Trying to shut down XMPP Monitoring thread before shutdown...", Level.INFO)
		_isRunning = false
	}

	/**
	 * This class tries to obtain local system's external IP by using a service from myexternalip.com
	 *
	 * @author Ronald Kurniawan (viper)
	 * @version 0.4.5, 7/03/14
	 * @see java.lang.Runnable
	 */
	class WgetExternalIP() extends Runnable {
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "-q", "-O", "-", "http://myexternalip.com/raw").start()
			val br = new BufferedReader(new InputStreamReader(wgetProc.getInputStream))
			val sb = new StringBuilder
			var line = br readLine()
			while (line != null) {
				sb append line
				line = br readLine()
			}
			wgetProc waitFor()
			_externalIP = Some(sb toString())
			br close()
			OUtils.ExternalIP_:(_externalIP getOrElse "127.0.0.1")
			LogWriter writeLog("External IP: " + _externalIP.getOrElse("N/A"), Level.INFO)
		}
	}

	/**
	 * Create a listener for our XMPP chat manager.
	 *
	 * @author Ronald Kurniawan (viper)
	 * @see org.jivesoftware.smack.ChatManagerListener
	 */
	class XMPPChatListener extends ChatManagerListener {
		/**
		 * Add an instance of <code>XMPPMessageListener</code> to our Chat object.
		 *
		 * @param chat Chat object
		 * @param createdLocally whether this chat is created locally
		 */
		override def chatCreated(chat: Chat, createdLocally: Boolean) {
			chat addMessageListener new XMPPMessageListener
		}
	}

	/**
	 * XMPPMessageListener process the commands sent to this daemon and respond accordingly.
	 *
	 * @author Ronald Kurniawan (viper)
	 * @see org.jivesoftware.smack.MessageListener
	 */
	class XMPPMessageListener extends MessageListener {
		private var _isTPBSearch: Boolean = false

		/**
		 * This method only process TPB search results. Since search results can exceed 4000 characters, we need to split
		 * the results into 4000-character blocks and prepend the block with "1/TOTAL-BLOCKS:::" so the other side can
		 * reassemble the blocks correctly.
		 *
		 * @param chat the Chat object
		 * @param message raw message string to process
		 */
		def processMessage(chat: Chat, message: Message) {
			if (message != null && message.getBody != null) {
				val response: String = parseMessage(message.getBody)
				if (!_isTPBSearch) chat sendMessage response
				else {
					// split the response into 4,000 chars blocks and send...
					val CHAR_LIMIT = 4000
					var chunks = (response length) / CHAR_LIMIT
					if ((response length) % CHAR_LIMIT > 0) chunks += 1
					var marker = 0
					while (marker < chunks) {
						val start = marker * CHAR_LIMIT
						val end = {
							if ((marker + 1) * CHAR_LIMIT > (response length)) response.length
							else (marker + 1) * CHAR_LIMIT
						}
						val substring = (marker + 1) + "/" + chunks + ":::" + (response substring(start, end))
						chat sendMessage substring
						marker += 1
						try {
							Thread sleep 1000
						}
						catch {
							case ie: InterruptedException =>
						}
					}

					_isTPBSearch = false
				}
			}
		}

		/**
		 * Process the command string received from the client and direct the received command to the proper processor.
		 * This method largely resembles the REST methods in FluxoWSProcess.java.
		 *
		 * @param msg command string received from the client
		 * @return string containing the result of the command
		 */
		def parseMessage(msg: String): String = {
			LogWriter writeLog("Received message: " + msg, Level.INFO)
			val words: Array[String] = msg split "\\s+"
			if (words.length < 2) return "ERR LENGTH"
			if (!(words(0) equals "DD")) return "ERR NOTIFIER"
			words(1) match {
				case "ADD_TORRENT" =>
					if (words.length < 4) "ERR LENGTH"
					else OAria processRequest(words(3), words(2), isHttp = false, "", "")
				case "ADD_URI" =>
					if (words.length < 4) "ERR LENGTH"
					else OAria processRequest(words(3), words(2), isHttp = true, "", "")
				case "ADD_URI_C" =>
					if (words.length < 6) "ERR LENGTH"
					else OAria processRequest(words(3), words(2), isHttp = true, words(4), words(5))
				case "STATUS" =>
					if (words.length < 3) "ERR LENGTH"
					else {
						val hMap = new util.HashMap[String, String]
						val tasks: Array[Task] = DbControl queryTasks words(2)
						for (t <- tasks) {
							val progress: Int = {
								if (t.TaskCompletedLength > 0 && t.TaskTotalLength > 0) ((t.TaskCompletedLength * 100) / t.TaskTotalLength).toInt
								else -1
							}
							val dlName: String = {
								if (t.TaskPackage.getOrElse(null).length > 1) t.TaskPackage.getOrElse(null)
								else "Unknown Download"
							}
							hMap put(dlName, String valueOf progress)
						}
						OUtils DownloadProgressToJson hMap
					}
				case "DELETE" =>
					if (words.length < 3) "ERR LENGTH"
					else {
						val arrayUnfinishedTasks: Array[Task] = DbControl queryUnfinishedTasks()
						for (t <- arrayUnfinishedTasks) {
							if ((t TaskGID) getOrElse "" equals words(2)) {
								DbControl removeTask words(2)
								// DELETE the files associated with this download
								val mainFile = new File((t TaskPackage) getOrElse "")
								if (mainFile exists()) OUtils deleteFile mainFile
								val ariaFile = new File(((t TaskPackage) getOrElse "") + ".aria2")
								if (ariaFile exists()) OUtils deleteFile ariaFile
								return "OK " + ((t TaskGID) getOrElse "") + " KILLED"
							}
						}
						"NOT FOUND"
					}
				case "YIFY" =>
					// the next command should be "LIST" or " DETAILS"
					// "LIST" has 3 parameters (total 6)
					// "DETAILS" has 1 parameter (total 4)
					if (words.length != 6 && words.length != 4) "ERR LENGTH, expected 4 or 6, found " + words.length
					else {
						words(2) match {
							case "LIST" =>
								if (words.length != 6) "ERR LENGTH: \"LIST\" requires 6 params, found " + words.length
								else {
									val intValidator = new IntegerValidator
									val page: Int = {
										val intObj: Option[Integer] = Some(intValidator validate words(3))
										intObj.getOrElse(1).asInstanceOf[Int]
									}
									val quality: Int = {
										val intObj: Option[Integer] = Some(intValidator validate words(4))
										intObj.getOrElse(0).asInstanceOf[Int]
									}
									val rating: Int = {
										val intObj: Option[Integer] = Some(intValidator validate words(5))
										intObj.getOrElse(0).asInstanceOf[Int]
									}
									YIFYP procListMovie(page, quality, rating, _externalIP.getOrElse("127.0.0.1"),
											OUtils.readConfig.HTTPPort)
								}
							case "DETAILS" =>
								if (words.length != 4) "ERR LENGTH: \"DETAILS\" requires 4 params, found " + words.length
								else {
									val intValidator = new IntegerValidator
									val id: Int = {
										val intObj: Option[Integer] = Some(intValidator validate words(3))
										intObj.getOrElse(-1).asInstanceOf[Int]
									}
									if (id > 0) YIFYP procMovieDetails(id, _externalIP.getOrElse("127.0.0.1"), OUtils.readConfig.HTTPPort)
									else "ERR CMD"
								}
							case "SEARCH" =>
								if (words.length != 4) "ERR LENGTH: \"SEARCH\" requires 4 params, found " + words.length
								else if (words.length >= 4 && (!words(3).startsWith("ST="))) "ERR SEARCH TERM"
								else {
									val searchTerm: String = {
										val ucodec = new URLCodec
										ucodec decode words(3) substring ("ST=" length) replaceAllLiterally("\"", "")
									}
									YIFYP procYIFYSearch searchTerm
								}
							case _ => "ERR CMD"
						}
					}
				case "TPB" =>
					val pm = OPlugin.getPluginManager
					// DEBUG
					LogWriter writeLog("PluginManager: " + pm.toString, Level.DEBUG)
					val tpbPlugin = (OPlugin getPluginManager) getPlugin classOf[TrTPB]
					if (tpbPlugin == null) "ERR PLUGIN NOT FOUND"
					else if (!((tpbPlugin primaryCommand()) equals "TPB")) "ERR WRONG PLUGIN"
					else {
						tpbPlugin setMailLoggerName "net.fluxo.MailLogger"
						_isTPBSearch = true
						tpbPlugin process words
					}
					// at the very least we need search term...
					// page and categories can also be added
					// syntax: DD TPB ST=[Search Term] PG=[page starting from 0] CAT=[comma-separated xxx code]
					/*if (words.length < 3) "ERR LENGTH"
					else {
						if (words.length == 3 && !words(2).startsWith("ST=")) "SYNTAX ERROR 1"
						else if (words.length == 4 && (!words(2).startsWith("ST=") || !words(3).startsWith("PG="))) "SYNTAX ERROR 2 "
						else if (words.length >= 5 && (!words(2).startsWith("ST=") || !words(3).startsWith("PG=") || !words(4).startsWith("CAT="))) "SYNTAX ERROR 3"
						else {
							val searchTerm: String = {
								val ucodec = new URLCodec
								ucodec decode words(2).substring("ST=".length) replaceAllLiterally("\"", "")
							}
							val page: Int = {
								if (words.length >= 4) {
									try {
										words(3).substring("PG=".length).toInt
									} catch {
										case nfe: NumberFormatException => 0
									}
								} else 0
							}
							val cat: Array[Int] = {
								if (words.length >= 5) {
									val cats = words(4).substring("CAT=".length).split(",")
									val c = new Array[Int](cats.length)
									var counter: Int = 0
									for (x <- cats) {
										c(counter) = x.toInt
										counter += 1
									}
									c
								} else Array[Int]()
							}

							_isTPBSearch = true
							TPBP query(searchTerm, page, cat)
						}
					}*/
				case "TPBDETAILS" =>
					val tpbPlugin = (OPlugin getPluginManager) getPlugin(classOf[TrTPB], new OptionCapabilities("targetSite:TPB"))
					if (tpbPlugin == null) "ERR PLUGIN NOT FOUND"
					else if (!((tpbPlugin primaryCommand()) equals "TPB")) "ERR WRONG PLUGIN"
					else {
						tpbPlugin setMailLoggerName "net.fluxo.MailLogger"
						tpbPlugin process words
					}
					/*if (words.length != 3) "ERR TPBDETAILS SYNTAX"
					else {
						val detailsURL = URLDecoder decode(words(2), "UTF-8")
						if (!(detailsURL startsWith "http://thepiratebay.se/")) "ERR TPBDETAILS URL"
						else {
							TPBP queryDetails (FilenameUtils getPath detailsURL)
						}
					}*/
				case "VIDEO" =>
					if (words.length != 4) "ERR VIDEO REQUEST LENGTH"
					else OVideoP processRequest(words(3), words(2))
				case _ => "ERR CMD"
			}
		}
	}

}
