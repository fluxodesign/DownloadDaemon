package net.fluxo.dd

import net.fluxo.dd.dbo.Config
import java.util.{Random, Properties}
import java.io.{IOException, FileInputStream}
import org.apache.log4j.Level
import java.net.ServerSocket

/**
 * User: Ronald Kurniawan (viper)
 * Date: 15/03/14
 * Time: 20:58 PM
 */
class Utils {

	private var _config: Option[Config] = None

	def readConfig: Config = {
		if (_config.isEmpty) _config = Some(readConfiguration)
		_config.getOrElse(null)
	}

	private def readConfiguration: Config = {
		val prop: Properties = new Properties
		var cfg: Config = new Config
		try {
			prop.load(new FileInputStream("./dd.properties"))
			cfg.RPCPort_= (java.lang.Integer.parseInt(prop.getProperty("rpc_port")))
			cfg.RPCLimit_=(java.lang.Integer.parseInt(prop.getProperty("rpc_limit")))
			cfg.HTTPDPort_=(java.lang.Integer.parseInt(prop.getProperty("httpd_port")))
			cfg.XMPPProvider_=(prop.getProperty("xmpp_provider"))
			cfg.XMPPAccount_=(prop.getProperty("xmpp_account"))
			cfg.XMPPPassword_=(prop.getProperty("xmpp_password"))
			cfg.DownloadDir_=(prop.getProperty("download_dir"))
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error reading properties file dd.properties", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
		cfg
	}

	def portInUse(port: Int): Boolean = {
		var status = false
		var ss: ServerSocket = null
		try {
			ss = new ServerSocket(port)
			ss.setReuseAddress(true)
		} catch {
			case ioe: IOException =>
				status = true
		} finally {
			if (ss != null) {
				ss.close()
			}
		}
		status
	}

	def generateGID(): String = {
		val char16: Array[Char] = new Array[Char](16)
		//[0-9A-F]
		val randomizer = new Random()
		for (x <- 0 until 16) {
			char16(x) = {
				val wordOrDigit = randomizer.nextBoolean()
			}
		}
		char16.toString
	}
}

object OUtils extends Utils