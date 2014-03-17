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
	private val _randomizer: Random = new Random(System.currentTimeMillis())

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
		// DEBUG
		System.out.println("Checking port " + port + "...")
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
		//[0-9A-F]
		val randomValue = _randomizer.nextLong()
		val sb = new StringBuilder(java.lang.Long.toHexString(randomValue))
		while (sb.length < 16) {
			sb.insert(0, "0")
		}
		sb.toString()
	}
}

object OUtils extends Utils
