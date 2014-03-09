package net.fluxo.dd

import java.sql.{Timestamp, DriverManager, Connection}
import org.apache.log4j.Level
import net.fluxo.dd.dbo.Task
import org.joda.time.DateTime
import scala.collection.mutable

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

	def addTask(task: Task): Boolean = {
		val insertStatement = """INSERT INTO input(gid,input,start,owner) VALUES(?,?,?,?)"""
		var response: Boolean = true
		try {
			val ps = _conn.prepareStatement(insertStatement)
			ps.setString(1, task.TaskGID.getOrElse(null))
			ps.setString(2, task.TaskInput.getOrElse(null))
			ps.setTimestamp(3, new Timestamp(task.TaskStarted))
			ps.setString(4, task.TaskOwner.getOrElse(null))
			val inserted = ps.executeUpdate()
			if (inserted == 0) {
				LogWriter.writeLog("Failed to insert new task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				response = false
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error inserting new task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
				if (response) response = false
		}
		response
	}

	def updateTask(task: Task): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET status = ?, completed_length = ?, total_length = ? WHERE gid = ? AND owner = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setString(1, task.TaskStatus.getOrElse(null))
			ps.setLong(2, task.TaskCompletedLength)
			ps.setLong(3, task.TaskTotalLength)
			ps.setString(4, task.TaskGID.getOrElse(null))
			ps.setString(5, task.TaskOwner.getOrElse(null))
			val updated = ps.executeUpdate()
			if (updated == 0) {
				LogWriter.writeLog("Failed to update task with GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				response = false
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error updating task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
				if (response) response = false
		}
		response
	}

	def finishTask(task: Task): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET end = ?, completed = ?, status = ?, completed_length = ? WHERE gid = ? AND owner = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setTimestamp(1, new Timestamp(DateTime.now().getMillis))
			ps.setBoolean(2, true)
			ps.setString(3, task.TaskStatus.getOrElse(null))
			ps.setLong(4, task.TaskCompletedLength)
			ps.setString(5, task.TaskGID.getOrElse(null))
			ps.setString(6, task.TaskOwner.getOrElse(null))
			val updated = ps.executeUpdate()
			if (updated == 0) {
				LogWriter.writeLog("Failed to update finished task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				response = false
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error updating status for finished task with GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
				if (response) response = false
		}
		response
	}

	def queryTasks(owner: String): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE owner = ? AND completed = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setString(1, owner)
			ps.setBoolean(2, false)
			val rs = ps.executeQuery()
			while (rs.next()) {
				mlist.+=(new Task {
					TaskGID_=(rs.getString("gid"))
					TaskInput_=(rs.getString("input"))
					TaskStarted_=(rs.getTimestamp("start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs.getBoolean("completed"))
					TaskOwner_=(rs.getString("owner"))
					TaskDirectory_=(rs.getString("directory"))
					TaskFile_=(rs.getString("file"))
					TaskStatus_=(rs.getString("status"))
					TaskTotalLength_=(rs.getLong("total_length"))
					TaskCompletedLength_=(rs.getLong("completed_length"))
				})
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying active task(s) for owner " + owner, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		mlist.toArray
	}

	def queryUnfinishedTasks(): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE completed = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setBoolean(1, false)
			val rs = ps.executeQuery()
			while (rs.next()) {
				mlist.+=(new Task {
					TaskGID_=(rs.getString("gid"))
					TaskInput_=(rs.getString("input"))
					TaskStarted_=(rs.getTimestamp("start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs.getBoolean("completed"))
					TaskOwner_=(rs.getString("owner"))
					TaskDirectory_=(rs.getString("directory"))
					TaskFile_=(rs.getString("file"))
					TaskStatus_=(rs.getString("status"))
					TaskTotalLength_=(rs.getLong("total_length"))
					TaskCompletedLength_=(rs.getLong("completed_length"))
				})
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying all active task(s)", Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		mlist.toArray
	}

	def replaceGID(oldGID: String, newGID: String, owner: String) {
		val updateStatement = """UPDATE input SET gid = ? WHERE gid = ? AND owner = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setString(1, newGID)
			ps.setString(2, oldGID)
			ps.setString(3, owner)
			val updated = ps.executeUpdate()
			if (updated == 0) {
				LogWriter.writeLog("Failed to replace new GID for GID: " + oldGID, Level.ERROR)
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error replacing new GID for GID: " + oldGID, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
	}

	def cleanup() {
		_conn.close()
	}
}

object DbControl extends DbManager