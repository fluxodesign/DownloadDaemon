package net.fluxo.dd

import java.sql.{Timestamp, DriverManager, Connection}
import org.apache.log4j.Level
import net.fluxo.dd.dbo.{YIFYCache, CountPackage, Task}
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
		val insertStatement = """INSERT INTO input(gid,input,start,owner,is_http,http_username,http_password) VALUES(?,?,?,?,?,?,?)"""
		var response: Boolean = true
		try {
			val ps = _conn.prepareStatement(insertStatement)
			ps.setString(1, task.TaskGID.getOrElse(null))
			ps.setString(2, task.TaskInput.getOrElse(null))
			ps.setTimestamp(3, new Timestamp(task.TaskStarted))
			ps.setString(4, task.TaskOwner.getOrElse(null))
			ps.setBoolean(5, task.TaskIsHttp)
			ps.setString(6, task.TaskHttpUsername.getOrElse(null))
			ps.setString(7, task.TaskHttpPassword.getOrElse(null))
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
		val updateStatement = """UPDATE input SET package = ?, status = ?, completed_length = ?, total_length = ?, info_hash = ? WHERE gid = ? AND tail_gid = ? AND owner = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setString(1, task.TaskPackage.getOrElse(null))
			ps.setString(2, task.TaskStatus.getOrElse(null))
			ps.setLong(3, task.TaskCompletedLength)
			ps.setLong(4, task.TaskTotalLength)
			ps.setString(5, task.TaskInfoHash.getOrElse("XXX"))
			ps.setString(6, task.TaskGID.getOrElse(null))
			ps.setString(7, task.TaskTailGID.getOrElse(null))
			ps.setString(8, task.TaskOwner.getOrElse(null))
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

	def updateTaskTailGID(gid: String, tailGid: String): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET tail_gid = ? WHERE gid = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setString(1, tailGid)
			ps.setString(2, gid)
			val updated = ps.executeUpdate()
			if (updated == 0) {
				LogWriter.writeLog("Failed to update tail GID for GID " + gid, Level.ERROR)
				response = false
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error updating tail GID for GID " + gid, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
				if (response) response = false
		}
		response
	}

	def queryFinishTask(tailGID: String, infoHash: String, tl: Long): CountPackage = {
		val cp: CountPackage = new CountPackage
		val queryStatement = """SELECT COUNT(*) AS count, package FROM input WHERE tail_gid = ? AND info_hash = ? AND total_length = ? AND completed = false"""
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setString(1, tailGID)
			ps.setString(2, infoHash)
			ps.setLong(3, tl)
			val rs = ps.executeQuery()
			while (rs.next()) {
				cp.CPCount_=(rs.getInt("count"))
				cp.CPPackage_=(rs.getString("package"))
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying almost finish task(s) with tail GID " + tailGID, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		cp
	}

	def isTaskGIDUsed(gid: String): Boolean = {
		var response: Boolean = false
		val queryStatement = """SELECT COUNT(*) AS count FROM input WHERE gid = ?"""
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setString(1, gid)
			val rs = ps.executeQuery()
			while (rs.next()) {
				if (rs.getInt("count") > 0) response = true
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying task GID for unfinished tasks: " + gid, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		response
	}

	def finishTask(status: String, cl: Long, tailGID: String, infoHash: String, tl: Long): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET end = ?, completed = ?, status = ?, completed_length = ? WHERE tail_gid = ? AND info_hash = ? AND total_length = ?"""
		try {
			val ps = _conn.prepareStatement(updateStatement)
			ps.setTimestamp(1, new Timestamp(DateTime.now().getMillis))
			ps.setBoolean(2, true)
			ps.setString(3, status)
			ps.setLong(4, cl)
			ps.setString(5, tailGID)
			ps.setString(6, infoHash)
			ps.setLong(7, tl)
			val updated = ps.executeUpdate()
			if (updated == 0) {
				LogWriter.writeLog("Failed to update finished task for tail GID " + tailGID, Level.ERROR)
				response = false
			}
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error updating status for finished task with GID " + tailGID, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
				if (response) response = false
		}
		response
	}

	def queryTask(gid: String): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE gid = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setString(1, gid)
			val rs = ps.executeQuery()
			while (rs.next()) {
				mlist.+=(new Task {
					TaskGID_=(rs.getString("gid"))
					TaskTailGID_=(rs.getString("tail_gid"))
					TaskInput_=(rs.getString("input"))
					TaskStarted_=(rs.getTimestamp("start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs.getBoolean("completed"))
					TaskOwner_=(rs.getString("owner"))
					TaskPackage_=(rs.getString("package"))
					TaskStatus_=(rs.getString("status"))
					TaskTotalLength_=(rs.getLong("total_length"))
					TaskCompletedLength_=(rs.getLong("completed_length"))
					TaskInfoHash_=(rs.getString("info_hash"))
					TaskIsHttp_=(rs.getBoolean("is_http"))
					TaskHttpUsername_=(rs.getString("http_username"))
					TaskHttpPassword_=(rs.getString("http_password"))
				})
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying task with GID " + gid, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		mlist.toArray
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
					TaskTailGID_=(rs.getString("tail_gid"))
					TaskInput_=(rs.getString("input"))
					TaskStarted_=(rs.getTimestamp("start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs.getBoolean("completed"))
					TaskOwner_=(rs.getString("owner"))
					TaskPackage_=(rs.getString("package"))
					TaskStatus_=(rs.getString("status"))
					TaskTotalLength_=(rs.getLong("total_length"))
					TaskCompletedLength_=(rs.getLong("completed_length"))
					TaskInfoHash_=(rs.getString("info_hash"))
					TaskIsHttp_=(rs.getBoolean("is_http"))
					TaskHttpUsername_=(rs.getString("http_username"))
					TaskHttpPassword_=(rs.getString("http_password"))
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
					TaskTailGID_=(rs.getString("tail_gid"))
					TaskInput_=(rs.getString("input"))
					TaskStarted_=(rs.getTimestamp("start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs.getBoolean("completed"))
					TaskOwner_=(rs.getString("owner"))
					TaskPackage_=(rs.getString("package"))
					TaskStatus_=(rs.getString("status"))
					TaskTotalLength_=(rs.getLong("total_length"))
					TaskCompletedLength_=(rs.getLong("completed_length"))
					TaskInfoHash_=(rs.getString("info_hash"))
					TaskIsHttp_=(rs.getBoolean("is_http"))
					TaskHttpUsername_=(rs.getString("http_username"))
					TaskHttpPassword_=(rs.getString("http_password"))
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

	def queryTaskTailGID(tailGid: String): Task = {
		val queryStatement = """SELECT * FROM input WHERE tail_gid = ? AND completed = ?"""
		val t = new Task
		try {
			val ps = _conn.prepareStatement(queryStatement)
			ps.setString(1, tailGid)
			ps.setBoolean(2, false)
			val rs = ps.executeQuery()
			while (rs.next()) {
				t.TaskGID_=(rs.getString("gid"))
				t.TaskTailGID_=(rs.getString("tail_gid"))
				t.TaskInput_=(rs.getString("input"))
				t.TaskStarted_=(rs.getTimestamp("start").getTime)
				t.TaskEnded_=(DateTime.now().minusYears(10).getMillis)
				t.IsTaskCompleted_=(rs.getBoolean("completed"))
				t.TaskOwner_=(rs.getString("owner"))
				t.TaskPackage_=(rs.getString("package"))
				t.TaskStatus_=(rs.getString("status"))
				t.TaskTotalLength_=(rs.getLong("total_length"))
				t.TaskCompletedLength_=(rs.getLong("completed_length"))
				t.TaskInfoHash_=(rs.getString("info_hash"))
				t.TaskIsHttp_=(rs.getBoolean("is_http"))
				t.TaskHttpUsername_=(rs.getString("http_username"))
				t.TaskHttpPassword_=(rs.getString("http_password"))
			}
			rs.close()
			ps.close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying task with tail GID " + tailGid, Level.ERROR)
				LogWriter.writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		t
	}

	def replaceGID(oldGID: String, newGID: String, owner: String) {
		val updateStatement = """UPDATE input SET gid = ?, tail_gid = ? WHERE gid = ? AND owner = ?"""
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

	def ycQueryCount(): Int = {
		var count = 0
		val queryStatement = """SELECT COUNT(*) AS count FROM YIFY_CACHE"""
		try {
			val ps = _conn prepareStatement queryStatement
			val rs = ps executeQuery()
			if (rs next()) {
				count = rs getInt "count"
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying YIFY cache count", Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		count
	}

	def ycQueryMovieID(movieID: Int): Boolean = {
		var status = false
		val queryStatement = """SELECT COUNT(*) AS count FROM YIFY_CACHE WHERE movie_id = ?"""
		try {
			val ps = _conn prepareStatement queryStatement
			ps setInt(1, movieID)
			val result = ps executeQuery()
			if (result next()) {
				if ((result getInt "count") > 0) status = true
			}
			result close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying YIFY cache for ID " + movieID, Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		status
	}

	def ycInsertNewData(obj: YIFYCache): Boolean = {
		val insertStatement = """INSERT INTO yify_cache(movie_id,title,year,quality,size,cover_image) VALUES(?,?,?,?,?,?)"""
		var response: Boolean = true
		try {
			val ps = _conn.prepareStatement(insertStatement)
			ps setInt (1, obj.MovieID)
			ps setString(2, obj.MovieTitle.getOrElse(""))
			ps setString(3, obj.MovieYear.getOrElse(""))
			ps setString (4, obj.MovieQuality.getOrElse(""))
			ps setString (5, obj.MovieSize.getOrElse(""))
			ps setString(6, obj.MovieCoverImage.getOrElse(""))
			val inserted = ps.executeUpdate()
			if (inserted == 0) {
				LogWriter.writeLog("Failed to insert new cache object for movie ID " + obj.MovieID, Level.ERROR)
				response = false
			} else LogWriter writeLog("Inserted " + (obj MovieID) + "/" + obj.MovieTitle.getOrElse("")
				+ " to DB", Level.INFO)
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error inserting new cache object for movie ID " + obj.MovieID, Level.ERROR)
				LogWriter writeLog(ex.getMessage , Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
				if (response) response = false
		}
		response
	}

	def ycQueryMoviesByTitle(title: String): Array[YIFYCache] = {
		val queryStatement = """SELECT * FROM yify_cache WHERE LOCATION(?,title) > 0"""
		val mlist = new mutable.MutableList[YIFYCache]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, title)
			val rs = ps.executeQuery()
			while (rs.next()) {
				mlist.+=(new YIFYCache {
					MovieID_:(rs getInt "movie_id")
					MovieTitle_:(rs getString "title")
					MovieYear_:(rs getString "year")
					MovieQuality_:(rs getString "quality")
					MovieSize_:(rs getString "size")
					MovieCoverImage_:(rs getString "cover_image")
				})
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter.writeLog("Error querying movies by title", Level.ERROR)
				LogWriter.writeLog(ex.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		mlist.toArray
	}

	def cleanup() {
		_conn.close()
	}
}

object DbControl extends DbManager
