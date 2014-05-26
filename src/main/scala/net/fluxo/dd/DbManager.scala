/*
 * DbManager.scala
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

import java.sql.{Timestamp, DriverManager, Connection}
import org.apache.log4j.Level
import net.fluxo.dd.dbo.{YIFYCache, CountPackage, Task}
import org.joda.time.DateTime
import scala.collection.mutable

/**
 * DbManager deals with queries, inserts and deletion of data in and out of the database.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 5/03/14
 */
class DbManager {

	private val _connString: String = "jdbc:h2:dddb;AUTO_RECONNECT=TRUE;LOCK_MODE=1"
	private var _conn: Connection = _

	/**
	 * Set up the database prior to any calls. Loads the driver and open a connection to the database.
	 */
	def setup() {
		try {
			Class forName "org.h2.Driver"
			_conn = DriverManager getConnection(_connString, "devel", "passwd")
			LogWriter writeLog("Initialising internal database...", Level.INFO)
		} catch {
			case e: Exception =>
				LogWriter writeLog("Error opening internal database", Level.ERROR)
				LogWriter writeLog(e.getMessage + " caused by " + e.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString e, Level.ERROR)
		}
	}

	/**
	 * Add a new download task into the database.
	 *
	 * @param task a <code>net.fluxo.dd.dbo.Task</code> object
	 * @return true if adding is successful; false otherwise
	 */
	def addTask(task: Task): Boolean = {
		val insertStatement = """INSERT INTO input(gid,input,start,owner,is_http,http_username,http_password) VALUES(?,?,?,?,?,?,?)"""
		var response: Boolean = true
		try {
			val ps = _conn prepareStatement insertStatement
			ps setString(1, task.TaskGID.getOrElse(null))
			ps setString(2, task.TaskInput.getOrElse(null))
			ps setTimestamp(3, new Timestamp(task.TaskStarted))
			ps setString(4, task.TaskOwner.getOrElse(null))
			ps setBoolean(5, task.TaskIsHttp)
			ps setString(6, task.TaskHttpUsername.getOrElse(null))
			ps setString(7, task.TaskHttpPassword.getOrElse(null))
			val inserted = ps executeUpdate()
			if (inserted == 0) {
				LogWriter writeLog("Failed to insert new task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				response = false
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error inserting new task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
				if (response) response = false
		}
		response
	}

	/**
	 * Update a download task record.
	 *
	 * @param task a <code>net.fluxo.dd.dbo.Task</code> object
	 * @return true if update is successful; false otherwise
	 */
	def updateTask(task: Task): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET package = ?, status = ?, completed_length = ?, total_length = ?, info_hash = ? WHERE gid = ? AND tail_gid = ? AND owner = ?"""
		try {
			val ps = _conn prepareStatement updateStatement
			ps setString(1, task.TaskPackage.getOrElse(null))
			ps setString(2, task.TaskStatus.getOrElse(null))
			ps setLong(3, task.TaskCompletedLength)
			ps setLong(4, task.TaskTotalLength)
			ps setString(5, task.TaskInfoHash.getOrElse("XXX"))
			ps setString(6, task.TaskGID.getOrElse(null))
			ps setString(7, task.TaskTailGID.getOrElse(null))
			ps setString(8, task.TaskOwner.getOrElse(null))
			val updated = ps executeUpdate()
			if (updated == 0) {
				LogWriter writeLog("Failed to update task with GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				response = false
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error updating task for GID " + task.TaskGID.getOrElse(null), Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
				if (response) response = false
		}
		response
	}

	/**
	 * Update a video download task record.
	 *
	 * @param gid the unique video download ID
	 * @param owner user ID of the owner of the download process
	 * @param targetFile name of the downloaded video
	 * @param status 'active' or 'completed'
	 * @param totalLength total video file size (in bytes)
	 * @param completedLength the size of the completed download so far (in bytes)
	 * @return true if updated is completed successfully; false othewise
	 */
	def updateVideoTask(gid: String, owner: String, targetFile: String, status: String, totalLength: Long, completedLength: Long): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET package = ?, status = ?, completed_length = ?, total_length = ?, completed = ? WHERE gid = ? AND owner = ?"""
		try {
			val ps = _conn prepareStatement updateStatement
			ps setString(1, targetFile)
			ps setString(2, status)
			ps setLong(3, completedLength)
			ps setLong(4, totalLength)
			ps setBoolean(5, totalLength == completedLength)
			ps setString(6, gid)
			ps setString(7, owner)
			val updated = ps executeUpdate()
			if (updated == 0) {
				LogWriter writeLog("Failed to update video task with GID " + gid, Level.ERROR)
				response = false
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error updating video task for GID " + gid, Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				if (response) response = false
		}
		response
	}

	/**
	 * Update a tail GID on a particular download, in case of a restarted download.
	 *
	 * @param gid a unique download ID
	 * @param tailGid a new tail GID for the download
	 * @return true if update is successful; false otherwise
	 */
	def updateTaskTailGID(gid: String, tailGid: String): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET tail_gid = ? WHERE gid = ?"""
		try {
			val ps = _conn prepareStatement updateStatement
			ps setString(1, tailGid)
			ps setString(2, gid)
			val updated = ps executeUpdate()
			if (updated == 0) {
				LogWriter writeLog("Failed to update tail GID for GID " + gid, Level.ERROR)
				response = false
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error updating tail GID for GID " + gid, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
				if (response) response = false
		}
		response
	}

	/**
	 * Query the number of near-finished/finished download tasks that has not been marked as "complete" in the database.
	 *
	 * @param tailGID the tail GID of queried process
	 * @param infoHash the infoHasah of queried process
	 * @param tl total length of download
	 * @return a <code>net.fluxo.dd.dbo.CountPackage</code> object
	 */
	def queryFinishTask(tailGID: String, infoHash: String, tl: Long): CountPackage = {
		val cp: CountPackage = new CountPackage
		val queryStatement = """SELECT COUNT(*) AS count, package FROM input WHERE tail_gid = ? AND info_hash = ? AND total_length = ? AND completed = false"""
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, tailGID)
			ps setString(2, infoHash)
			ps setLong(3, tl)
			val rs = ps executeQuery()
			while (rs.next) {
				cp.CPCount_=(rs.getInt("count"))
				cp.CPPackage_=(rs.getString("package"))
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying almost finish task(s) with tail GID " + tailGID, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		cp
	}

	/**
	 * Check if a unique ID has already been used before.
	 *
	 * @param gid a unique ID
	 * @return true if the ID has been used before; false otherwise
	 */
	def isTaskGIDUsed(gid: String): Boolean = {
		var response: Boolean = false
		val queryStatement = """SELECT COUNT(*) AS count FROM input WHERE gid = ?"""
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, gid)
			val rs = ps executeQuery()
			while (rs.next) {
				if ((rs getInt "count") > 0) response = true
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying task GID for unfinished tasks: " + gid, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter.stackTraceToString(ex), Level.ERROR)
		}
		response
	}

	/**
	 * Update the database to mark that a particular download task is finished.
	 *
	 * @param status status to be updated into the database
	 * @param cl completed download length (should match total_length)
	 * @param tailGID tail GID of this download
	 * @param infoHash infoHash of this download
	 * @param tl total length of this download
	 * @return true if status updated successfully; false otherwise
	 */
	def finishTask(status: String, cl: Long, tailGID: String, infoHash: String, tl: Long): Boolean = {
		var response: Boolean = true
		val updateStatement = """UPDATE input SET end = ?, completed = ?, status = ?, completed_length = ? WHERE tail_gid = ? AND info_hash = ? AND total_length = ?"""
		try {
			val ps = _conn prepareStatement updateStatement
			ps setTimestamp(1, new Timestamp(DateTime.now().getMillis))
			ps setBoolean(2, true)
			ps setString(3, status)
			ps setLong(4, cl)
			ps setString(5, tailGID)
			ps setString(6, infoHash)
			ps setLong(7, tl)
			val updated = ps executeUpdate()
			if (updated == 0) {
				LogWriter writeLog("Failed to update finished task for tail GID " + tailGID, Level.ERROR)
				response = false
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error updating status for finished task with GID " + tailGID, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
				if (response) response = false
		}
		response
	}

	/**
	 * Query the database for the list of unfinished video download tasks.
	 *
	 * @return an array of <code>net.fluxo.dd.dbo.Task</code>
	 */
	def queryActiveVideoTask(): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE info_hash = ? AND tail_gid = ? AND completed = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, "noinfohash")
			ps setString(2, "notailgid")
			ps setBoolean(3, false)
			val rs = ps executeQuery()
			while (rs.next) {
				mlist.+=(new Task {
					TaskGID_=(rs getString "gid")
					TaskTailGID_=(rs getString "tail_gid")
					TaskInput_=(rs getString "input")
					TaskStarted_=((rs getTimestamp "start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs getBoolean "completed")
					TaskOwner_=(rs getString "owner")
					TaskPackage_=(rs getString "package")
					TaskStatus_=(rs getString "status")
					TaskTotalLength_=(rs getLong "total_length")
					TaskCompletedLength_=(rs getLong "completed_length")
					TaskInfoHash_=(rs getString "info_hash")
					TaskIsHttp_=(rs getBoolean "is_http")
					TaskHttpUsername_=(rs getString "http_username")
					TaskHttpPassword_=(rs getString "http_password")
				})
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying unfinished video tasks", Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		mlist.toArray
	}

	/**
	 * Query if a <code>Task</code> with particular ID exists in the database.
	 *
	 * @param gid unique download ID
	 * @return an array of <code>net.fluxo.dd.dbo.Task</code>
	 */
	def queryTask(gid: String): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE gid = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, gid)
			val rs = ps executeQuery()
			while (rs.next) {
				mlist.+=(new Task {
					TaskGID_=(rs getString "gid")
					TaskTailGID_=(rs getString "tail_gid")
					TaskInput_=(rs getString "input")
					TaskStarted_=((rs getTimestamp "start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs getBoolean "completed")
					TaskOwner_=(rs getString "owner")
					TaskPackage_=(rs getString "package")
					TaskStatus_=(rs getString "status")
					TaskTotalLength_=(rs getLong "total_length")
					TaskCompletedLength_=(rs getLong "completed_length")
					TaskInfoHash_=(rs getString "info_hash")
					TaskIsHttp_=(rs getBoolean "is_http")
					TaskHttpUsername_=(rs getString "http_username")
					TaskHttpPassword_=(rs getString "http_password")
				})
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying task with GID " + gid, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		mlist.toArray
	}

	/**
	 * Query unfinished tasks that belongs to a specific owner.
	 *
	 * @param owner user ID
	 * @return an array of <code>net.fluxo.dd.dbo.Task</code>
	 */
	def queryTasks(owner: String): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE owner = ? AND completed = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, owner)
			ps setBoolean(2, false)
			val rs = ps.executeQuery()
			while (rs.next) {
				mlist.+=(new Task {
					TaskGID_=(rs getString "gid")
					TaskTailGID_=(rs getString "tail_gid")
					TaskInput_=(rs getString "input")
					TaskStarted_=((rs getTimestamp "start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs getBoolean "completed")
					TaskOwner_=(rs getString "owner")
					TaskPackage_=(rs getString "package")
					TaskStatus_=(rs getString "status")
					TaskTotalLength_=(rs getLong "total_length")
					TaskCompletedLength_=(rs getLong "completed_length")
					TaskInfoHash_=(rs getString "info_hash")
					TaskIsHttp_=(rs getBoolean "is_http")
					TaskHttpUsername_=(rs getString "http_username")
					TaskHttpPassword_=(rs getString "http_password")
				})
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying active task(s) for owner " + owner, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		mlist.toArray
	}

	/**
	 * Select all unfinished tasks.
	 *
	 * @return an array of <code>net.fluxo.dd.dbo.Task</code>
	 */
	def queryUnfinishedTasks(): Array[Task] = {
		val queryStatement = """SELECT * FROM input WHERE completed = ?"""
		val mlist = new mutable.MutableList[Task]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setBoolean(1, false)
			val rs = ps executeQuery()
			while (rs.next) {
				mlist.+=(new Task {
					TaskGID_=(rs getString "gid")
					TaskTailGID_=(rs getString "tail_gid")
					TaskInput_=(rs getString "input")
					TaskStarted_=((rs getTimestamp "start").getTime)
					TaskEnded_=(DateTime.now().minusYears(10).getMillis)
					IsTaskCompleted_=(rs getBoolean "completed")
					TaskOwner_=(rs getString "owner")
					TaskPackage_=(rs getString "package")
					TaskStatus_=(rs getString "status")
					TaskTotalLength_=(rs getLong "total_length")
					TaskCompletedLength_=(rs getLong "completed_length")
					TaskInfoHash_=(rs getString "info_hash")
					TaskIsHttp_=(rs getBoolean "is_http")
					TaskHttpUsername_=(rs getString "http_username")
					TaskHttpPassword_=(rs getString "http_password")
				})
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying all active task(s)", Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		mlist.toArray
	}

	/**
	 * Remove a task from the database.
	 *
	 * @param taskGID a unique ID
	 */
	def removeTask(taskGID: String) {
		val queryStatement = """DELETE FROM input WHERE GID = ?"""
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, taskGID)
			ps executeUpdate()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error deleting unfinished task " + taskGID, Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
	}

	/**
	 * Returns all unfinished tasks with a specified tail GID.
	 *
	 * @param tailGid tail GID to query
	 * @return a <code>net.fluxo.dd.dbo.Task</code> object
	 */
	def queryTaskTailGID(tailGid: String): Task = {
		val queryStatement = """SELECT * FROM input WHERE tail_gid = ? AND completed = ?"""
		val t = new Task
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, tailGid)
			ps setBoolean(2, false)
			val rs = ps.executeQuery()
			while (rs.next()) {
				t.TaskGID_=(rs getString "gid")
				t.TaskTailGID_=(rs getString "tail_gid")
				t.TaskInput_=(rs getString "input")
				t.TaskStarted_=((rs getTimestamp "start").getTime)
				t.TaskEnded_=(DateTime.now().minusYears(10).getMillis)
				t.IsTaskCompleted_=(rs getBoolean "completed")
				t.TaskOwner_=(rs getString "owner")
				t.TaskPackage_=(rs getString "package")
				t.TaskStatus_=(rs getString "status")
				t.TaskTotalLength_=(rs getLong "total_length")
				t.TaskCompletedLength_=(rs getLong "completed_length")
				t.TaskInfoHash_=(rs getString "info_hash")
				t.TaskIsHttp_=(rs getBoolean "is_http")
				t.TaskHttpUsername_=(rs getString "http_username")
				t.TaskHttpPassword_=(rs getString "http_password")
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying task with tail GID " + tailGid, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		t
	}

	/**
	 * Replace an old task ID with a new task ID.
	 *
	 * @param oldGID old unique task ID
	 * @param newGID new unique task ID
	 * @param owner user ID of the download
	 */
	def replaceGID(oldGID: String, newGID: String, owner: String) {
		val updateStatement = """UPDATE input SET gid = ?, tail_gid = ? WHERE gid = ? AND owner = ?"""
		try {
			val ps = _conn prepareStatement updateStatement
			ps setString(1, newGID)
			ps setString(2, oldGID)
			ps setString(3, owner)
			val updated = ps executeUpdate()
			if (updated == 0) {
				LogWriter writeLog("Failed to replace new GID for GID: " + oldGID, Level.ERROR)
			}
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error replacing new GID for GID: " + oldGID, Level.ERROR)
				LogWriter writeLog(ex.getMessage + " caused by " + ex.getCause.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
	}

	/**
	 * Returns the total number of items in YIFY cache table.
	 *
	 * @return total number of YIFY cache items
	 */
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

	/**
	 * Query if a movie with a specified ID exists in YIFY cache table.
	 *
	 * @param movieID movie ID to query
	 * @return true if a movie is found; false otherwise
	 */
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

	/**
	 * Insert a new movie data into YIFY cache table.
	 *
	 * @param obj a <code>net.fluxo.dd.dbo.YIFYCache</code> object
	 * @return true if insert is successful; false otherwise
	 */
	def ycInsertNewData(obj: YIFYCache): Boolean = {
		val insertStatement = """INSERT INTO yify_cache(movie_id,title,year,quality,size,cover_image) VALUES(?,?,?,?,?,?)"""
		var response: Boolean = true
		try {
			val ps = _conn prepareStatement insertStatement
			ps setInt (1, obj.MovieID)
			ps setString(2, obj.MovieTitle.getOrElse(""))
			ps setString(3, obj.MovieYear.getOrElse(""))
			ps setString (4, obj.MovieQuality.getOrElse(""))
			ps setString (5, obj.MovieSize.getOrElse(""))
			ps setString(6, obj.MovieCoverImage.getOrElse(""))
			val inserted = ps executeUpdate()
			if (inserted == 0) {
				LogWriter writeLog("Failed to insert new cache object for movie ID " + obj.MovieID, Level.ERROR)
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

	/**
	 * Search for a movie in YIFY cache table based on the title.
	 *
	 * @param title full or partial search term
	 * @return an array of <code>net.fluxo.dd.dbo.YIFYCache</code> objects
	 */
	def ycQueryMoviesByTitle(title: String): Array[YIFYCache] = {
		val queryStatement = """SELECT * FROM yify_cache WHERE LOWER(title) LIKE ?"""
		val mlist = new mutable.MutableList[YIFYCache]
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, "%" + title.toLowerCase + "%")
			val rs = ps executeQuery()
			var counter = 0
			while (rs.next()) {
				mlist.+=(new YIFYCache {
					MovieID_:(rs getInt "movie_id")
					MovieTitle_:(rs getString "title")
					MovieYear_:(rs getString "year")
					MovieQuality_:(rs getString "quality")
					MovieSize_:(rs getString "size")
					MovieCoverImage_:(rs getString "cover_image")
				})
				counter += 1
			}
			rs close()
			ps close()
			LogWriter writeLog("ycQueryMoviesByTitle: " + counter + " found", Level.INFO)
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error querying movies by title", Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		mlist.toArray
	}

	/**
	 * Match the credentials pair to the one already in the database. The password is
	 * a hashed string.
	 *
	 * @param username username to check
	 * @param password password to check
	 * @return true if credentials matched; false otherwise
	 */
	def authCredentials(username: String, password: String): Boolean = {
		var status = false
		val hashed = OUtils hashString password
		val queryStatement = """SELECT COUNT(*) AS count FROM CREDS WHERE USERNAME = ? AND PASSWORD = ?"""
		try {
			val ps = _conn prepareStatement queryStatement
			ps setString(1, username)
			ps setString(2, hashed)
			val rs = ps executeQuery()
			while (rs.next) {
				if ((rs getInt "count") > 0) status = true
			}
			rs close()
			ps close()
		} catch {
			case ex: Exception =>
				LogWriter writeLog("Error authenticating credentials", Level.ERROR)
				LogWriter writeLog(ex.getMessage, Level.ERROR)
				LogWriter writeLog(LogWriter stackTraceToString ex, Level.ERROR)
		}
		status
	}

	/**
	 * Close the connection to the database.
	 */
	def cleanup() {
		_conn.close()
	}
}

/**
 * A Singleton object for DbManager
 */
object DbControl extends DbManager
