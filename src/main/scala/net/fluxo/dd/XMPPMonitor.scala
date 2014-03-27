package net.fluxo.dd

import org.jivesoftware.smack._
import org.apache.log4j.Level
import org.joda.time.DateTime
import org.jivesoftware.smack.packet.{Presence, Message}
import net.fluxo.dd.dbo.Task
import org.apache.commons.validator.routines.IntegerValidator
import java.io.{InputStreamReader, BufferedReader}

/**
 * User: Ronald Kurniawan (viper)
 * Date: 7/03/14
 * Time: 11:51 AM
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

	def setup() {
		_smackConfig = {
			if (xmppProvider.equals("google")) new ConnectionConfiguration(xmppServer, xmppPort, "gmail.com")
			else new ConnectionConfiguration(xmppServer, xmppPort, xmppServer)
		}
		_smackConfig.setReconnectionAllowed(true)
		_smackConfig.setSendPresence(true)
		_smackConfig.setSASLAuthenticationEnabled(true)
		_smackConfig.setRosterLoadedAtLogin(true)
		_smackConfig.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled)
		new Thread(new WgetExternalIP).start()
	}

	def connect(): Boolean = {
		_xmppc = new XMPPConnection(_smackConfig)
		LogWriter.writeLog("Connecting to chat server '" + xmppServer + "'...", Level.INFO)
		val currentTime: Long = DateTime.now().getMillis
		while (!_xmppConnected && withinMinuteRange(currentTime, 2)) {
			try {
				SASLAuthentication.supportSASLMechanism("PLAIN", 0)
				_xmppc.connect()
				if (_xmppc.isConnected) {
					_xmppConnected = true
					LogWriter.writeLog("Connected to chat server", Level.INFO)
				} else {
					try {
						Thread.sleep(1000)
					} catch {
						case ie: InterruptedException => ie.printStackTrace()
					}
				}
			} catch {
				case xmppEx: XMPPException =>
				    LogWriter.writeLog("Failed to connect to chat server", Level.ERROR)
					LogWriter.writeLog(xmppEx.getMessage, Level.ERROR)
					if (xmppEx.getXMPPError != null) {
						LogWriter.writeLog("XMPP ERROR: " + xmppEx.getXMPPError.getMessage, Level.ERROR)
					}
					LogWriter.writeLog(LogWriter.stackTraceToString(xmppEx), Level.ERROR)
				case ex: Exception =>
					LogWriter.writeLog("Failed to connect to chat server", Level.ERROR)
					LogWriter.writeLog(ex.getMessage, Level.ERROR)
					LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
			}
		}
		if (!_xmppConnected) {
			LogWriter.writeLog("Failed to connect to chat server", Level.ERROR)
			return false
		}
		var retries = 5
		while (!_xmppAuthenticated && retries > 0) {
			try {
				_xmppc.login(xmppAccount, xmppPassword)
				_xmppAuthenticated = _xmppc.isAuthenticated
				LogWriter.writeLog("XMPP Credentials authenticated", Level.INFO)
				setupListener()
				val presence: Presence = new Presence(Presence.Type.available)
				_xmppc.sendPacket(presence)
			} catch {
				case xmppEx: XMPPException =>
				    LogWriter.writeLog("Failed to authenticate to XMPP server", Level.ERROR)
					LogWriter.writeLog(xmppEx.getMessage, Level.ERROR)
					if (xmppEx.getXMPPError != null) {
						LogWriter.writeLog("XMPP ERROR: " + xmppEx.getXMPPError.getMessage, Level.ERROR)
					}
				    LogWriter.writeLog(LogWriter.stackTraceToString(xmppEx), Level.ERROR)
					retries -= 1
			}
		}
		_xmppc.isAuthenticated
	}

	def setupListener() {
		if (_xmppAuthenticated) {
			_xmppc.getChatManager.addChatListener(new XMPPChatListener)
		}
	}

	def isXmppConnected: Boolean = _xmppConnected

	def isXmppAuthenticated: Boolean = _xmppAuthenticated

	def withinMinuteRange(startPoint: Long, range: Int): Boolean = {
		val current: Long = DateTime.now().getMillis
		val startTime: DateTime = new DateTime(startPoint)
		startTime.plusMinutes(range).getMillis >= current
	}

	def secondsToMillis(secs: Long): Long = {
		secs * 1000
	}

	def  getOpSys : String = {
		val rawOS = System.getProperty("os.name").toLowerCase
		var retVal = rawOS
		if (rawOS.indexOf("win") >= 0) {
			retVal = "windows"
		} else if (rawOS.indexOf("mac") >= 0) {
			retVal = "mac"
		} else if (rawOS.indexOf("nix") >= 0 || rawOS.indexOf("aix") >= 0) {
			retVal = "unix"
		} else if (rawOS.indexOf("nux") >= 0) {
			retVal = "linux"
		} else if (rawOS.indexOf("sunos") >= 0 || rawOS.indexOf("solaris") >= 0) {
			retVal = "solaris"
		}
		retVal
	}

	def isServerReachable(url: String): Boolean = {
		var switch: String = ""
		getOpSys match {
			case "windows" => switch = "-n"
			case _ => switch = "-c"
		}
		val pb = new ProcessBuilder("ping", switch, "1", url)
		val proc = pb.start()

		val returnVal = proc.waitFor()
		returnVal == 0
	}

	private var _isXmppServerReachable: Boolean = false
	override def run() {
		setup()
		var whatToDo = "tryReachServer"
		while (_isRunning) whatToDo match {
			case "tryReachServer" =>
				var retries: Int = 0
				LogWriter.writeLog("Contacting chat server", Level.INFO)
				try {
					while (!_isXmppServerReachable) {
						_isXmppServerReachable = isServerReachable(xmppServer)
						if (!_isXmppServerReachable) {
							if (retries >= 20) {
								LogWriter.writeLog("XMPP Server is not contactable after 20 retries. Retrying in 5 minutes",
									Level.INFO)
								Thread.interrupted()
								Thread.sleep(secondsToMillis(5 * 60))
								retries = 0
							} else {
								LogWriter.writeLog("XMPP Server not responding. Retrying in 20 seconds...", Level.INFO)
								Thread.interrupted()
								Thread.sleep(secondsToMillis(20))
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
					LogWriter.writeLog("Connecting to XMPP Server...", Level.INFO)
					var retries: Int = 0
					var connected = false
					// We are going to establish connection here; If failing to connect, we will wait for 20 seconds
					// before retrying to connect again; We will try 20 times before resting for 5 minutes...
					while (!connected) try {
						connected = connect()
						if (!connected) {
							if (retries >= 20) {
								LogWriter.writeLog("Connecting to XMPP server '" + xmppServer +
									"' has been unsuccessful after 20 retries. Retrying in 5 minutes...", Level.INFO)
								Thread.interrupted()
								Thread.sleep(secondsToMillis(5 * 60))
								retries = 0
							} else {
								LogWriter.writeLog("Connecting to XMPP server '" + xmppServer +
									"' failed. Retrying in 10 seconds...", Level.INFO)
								Thread.interrupted()
								Thread.sleep(secondsToMillis(10))
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
					_xmppc.disconnect()
					whatToDo = "tryConnectServer"
				} else {
					try {
						Thread.interrupted()
						Thread.sleep(secondsToMillis(10))
					} catch {
						case ie: InterruptedException =>
							if (!_isRunning) {
								cleanup()
							}
					}
				}
		}
	}

	def cleanup() {
		if (_xmppc != null) {
			if (_xmppc.isConnected) _xmppc.disconnect()
		}
		LogWriter.writeLog("XMPP Monitoring thread shut down!", Level.INFO)
	}

	def stop() {
		LogWriter.writeLog("Trying to shut down XMPP Monitoring thread before shutdown...", Level.INFO)
		_isRunning = false
	}

	class WgetExternalIP() extends Runnable {
		override def run() {
			val wgetProc = new ProcessBuilder("wget", "-q", "-O", "-", "http://myexternalip.com/raw").start()
			val br = new BufferedReader(new InputStreamReader(wgetProc.getInputStream))
			val sb = new StringBuilder
			var line = br.readLine()
			while (line != null) {
				sb.append(line)
				line = br.readLine()
			}
			wgetProc.waitFor()
			_externalIP = Some(sb toString())
			br.close()
			LogWriter.writeLog("External IP: " + _externalIP.getOrElse("N/A"), Level.INFO)
		}
	}

	class XMPPChatListener extends ChatManagerListener {
		def chatCreated(chat: Chat, createdLocally: Boolean) {
			chat.addMessageListener(new XMPPMessageListener)
		}
	}

	class XMPPMessageListener extends MessageListener {
		def processMessage(chat: Chat, message: Message) {
			if (message != null && message.getBody != null) {
				val response: String = parseMessage(message.getBody)
				chat.sendMessage(response)
			}
		}

		def parseMessage(msg: String): String = {
			LogWriter.writeLog("Received message: " + msg, Level.INFO)
			val words: Array[String] = msg.split("\\s+")
			if (words.length < 2) return "ERR LENGTH"
			if (!words(0).equals("DD")) return "ERR NOTIFIER"
			words(1) match {
				case "ADD_TORRENT" =>
					if (words.length < 4) "ERR LENGTH"
					OAria.processRequest(words(3), words(2), isHttp = false, "", "")
				case "ADD_URI" =>
					if (words.length < 4) "ERR LENGTH"
					OAria.processRequest(words(3), words(2), isHttp = true, "", "")
				case "ADD_URI_C" =>
					if (words.length < 6) "ERR LENGTH"
					OAria.processRequest(words(3), words(2), isHttp = true, words(4), words(5))
				case "STATUS" =>
					if (words.length < 3) "ERR LENGTH"
					val tasks: Array[Task] = DbControl.queryTasks(words(2))
					val sb: StringBuilder = new StringBuilder
					for (t <- tasks) {
						val progress: Int = {
							if (t.TaskCompletedLength > 0 && t.TaskTotalLength > 0) ((t.TaskCompletedLength * 100)/ t.TaskTotalLength).toInt
							else -1
						}
						val dlName: String = {
							if (t.TaskPackage.getOrElse(null).length > 1) t.TaskPackage.getOrElse(null)
							else "Unknown Download"
						}
						sb.append(dlName + " --> " + progress + "%" + System.lineSeparator())
					}
					if (tasks.length == 0) sb.append("No active tasks are running!")
					sb.toString()
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
										OUtils.readConfig.HTTPDPort)
								}
							case "DETAILS" =>
								if (words.length != 4) "ERR LENGTH: \"DETAILS\" requires 4 params, found " + words.length
								else {
									val intValidator = new IntegerValidator
									val id: Int = {
										val intObj: Option[Integer] = Some(intValidator validate words(3))
										intObj.getOrElse(-1).asInstanceOf[Int]
									}
									if (id > 0) YIFYP procMovieDetails id
									else "ERR CMD"
								}
							case _  => "ERR CMD"
						}
					}
				case "TPB" =>
					// at the very least we need search term...
					// page and categories can also be added
					// syntax: DD TPB ST=[Search Term] PG=[page starting from 0] CAT=[comma-separated xxx code]
					if (words.length < 3) "ERR LENGTH"
					else {
						if (words.length == 3 && !words(2).startsWith("ST=")) "SYNTAX ERROR 1"
						else if (words.length == 4 && (!words(2).startsWith("ST=") || !words(3).startsWith("PG="))) "SYNTAX ERROR 2 "
						else if (words.length >= 5 && (!words(2).startsWith("ST=") || !words(3).startsWith("PG=") || !words(4).startsWith("CAT="))) "SYNTAX ERROR 3"
						else {
							val searchTerm: String = {
								words(2).substring("ST=".length).replaceAllLiterally("\"", "")
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

							TPBP query(searchTerm, page, cat)
						}
					}
				case _ => "ERR CMD"
			}
		}
	}
}
