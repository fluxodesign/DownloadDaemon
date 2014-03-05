package net.fluxo.dd

import java.sql.{DriverManager, Connection}
import org.apache.log4j.Level
import net.fluxo.dd.dbo.Task

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/03/14
 * Time: 2:45 PM
 * Comment:
 */
class DbManager {

	private val _connString: String = "jdbc:h2:dddb;AUTO_RECONNECT=TRUE;LOCK_MODE=1"
	private var _conn: Connection = _

	def setup() {
		try {
			Class.forName("org.h2.Driver")
			_conn = DriverManager.getConnection(_connString, "devel", "passwd")
			LogWriter.writeLog("Initialising internal database...", Level.INFO)
		} catch {
			case e: Exception =>
				LogWriter.writeLog("Error opening internal database", Level.ERROR)
				LogWriter.writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(e), Level.ERROR)
		}
	}

	def addTask(task: Task) {
		val insertStatement = """INSERT INTO input(gid,input,start,rpc_port) VALUES(?,?,?,?)"""
	}

	def cleanup() {
		_conn.close()
	}
}

object DbControl extends DbManager