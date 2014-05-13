/*
 * YIFYCacheJob.scala
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

import org.quartz.{JobExecutionException, JobExecutionContext, Job}
import org.apache.log4j.Level
import java.util.concurrent.{TimeUnit, Future, Callable, Executors}
import org.json.simple.{JSONArray, JSONObject}
import org.json.simple.parser.JSONParser
import net.fluxo.dd.dbo.YIFYCache

/**
 * This class represents a <code>Job</code> that processes and updates the YIFY cache. This class is being
 * called from <code>YIFYCacheMonitor</code>.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 3/04/14
 * @see org.quartz.Job
 */
class YIFYCacheJob extends Job {

	private val _yifyCacheService = Executors newFixedThreadPool 1

	/**
	 * Start the YCache process to update the YIFY cache and wait for the process to finish.
	 *
	 * @param context a <code>org.quartz.JobExecutionContext</code> object
	 * @throws org.quartz.JobExecutionException JobExecutionException
	 */
	@throws(classOf[JobExecutionException])
	override def execute(context: JobExecutionContext) {
		LogWriter writeLog("Starting YIFY Cache Updater", Level.INFO)
		val c: Callable[String] = new YCache
		val future: Future[String] = _yifyCacheService submit c
		while (!(future isDone)) {
			try {
				Thread sleep 2000
			} catch {
				case ie: InterruptedException =>
			}
		}
		_yifyCacheService shutdown()
		try {
			_yifyCacheService awaitTermination(1, TimeUnit.HOURS)
		} catch {
			case ie: InterruptedException =>
				LogWriter writeLog("Something happened on the way to YIFY caching...", Level.ERROR)
				LogWriter writeLog(ie.getMessage, Level.ERROR)
		}
	}
}

/**
 * YCache attempts to populate the cache database and monitor the YIFY site for new items.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 3/04/2014
 * @see java.util.concurrent.Callable
 */
class YCache extends Callable[String] {
	private var _pageNo = 0
	private var _totalPageNo = 0

	/**
	 * If there are no items in our cache database, this method will then populate the database by calling for
	 * every items from the YIFY server. Otherwise, it will attempt to process the first 4 pages to check if
	 * there are new items out there.
	 *
	 * @throws java.lang.Exception Any exception
	 * @return "OK"
	 */
	@throws(classOf[Exception])
	override def call(): String = {
		var totalItemsReported = 0L
		val jsonParser = new JSONParser
		val totalItemsInDb = DbControl ycQueryCount()
		if (totalItemsInDb == 0) populateDb()
		else {
			_pageNo += 1
			var response = YIFYP procYIFYCache _pageNo
			if (_totalPageNo == 0 || _pageNo < _totalPageNo) {
				try {
					val obj = (jsonParser parse response).asInstanceOf[JSONObject]
					if (_totalPageNo == 0) {
						val movieCount = (obj get "MovieCount").asInstanceOf[Long]
						totalItemsReported = movieCount
						_totalPageNo = (movieCount / 50).asInstanceOf[Int]
						if (movieCount % 50 > 0) _totalPageNo += 1
					}
				} catch {
					case jse: Exception =>
						LogWriter writeLog("Error parsing JSON from YIFY movie list", Level.ERROR)
						LogWriter writeLog(jse.getMessage, Level.ERROR)
				}
			}
			var statusTrueCount = 0
			var mPageNo = 1
			while (statusTrueCount < 4 && mPageNo < _totalPageNo) {
				LogWriter writeLog("UPDATE: Processing page " + mPageNo + "/" + _totalPageNo, Level.INFO)
				response = YIFYP procYIFYCache mPageNo
				val status = processEntry(response, jsonParser)
				if (status) statusTrueCount += 1
				mPageNo += 1
			}
		}
		"OK"
	}

	/**
	 * Obtains the total movie count from YIFY server, calculate the total number of pages then
	 * start processing the entries to input into the database.
	 */
	private def populateDb() {
		// calculate the total pages
		_pageNo += 1
		var response = YIFYP procYIFYCache _pageNo
		try {
			val jsonParser = new JSONParser
			val obj = (jsonParser parse response).asInstanceOf[JSONObject]
			val movieCount = (obj get "MovieCount").asInstanceOf[Long]
			_totalPageNo = (movieCount / 50).asInstanceOf[Int]
			if (movieCount % 50 > 0) _totalPageNo += 1
			// and then start populating the DB backwards (from last set)
			_pageNo = _totalPageNo
			while (_pageNo > 0) {
				LogWriter writeLog("POPULATE: Processing page " + _pageNo, Level.INFO)
				response = YIFYP procYIFYCache _pageNo
				processEntry(response, jsonParser)
				_pageNo -= 1
			}
		} catch {
			case jse: Exception =>
				LogWriter writeLog("Error populating YIFY cache db", Level.ERROR)
				LogWriter writeLog(jse.getMessage, Level.ERROR)
		}
	}

	/**
	 * Process a single page from YIFY, extract every items from the page, convert the item into a
	 * <code>YIFYCache</code> object and then insert it into the database.
	 * @param raw response string from YIFY server
	 * @param jsonParser an instance of <code>org.json.simple.parser.JSONParser</code>
	 * @return a Boolean value
	 */
	private def processEntry(raw: String, jsonParser: JSONParser): Boolean = {
		var status = true
		try {
			val obj = (jsonParser parse raw).asInstanceOf[JSONObject]
			val iterator = (obj get "MovieList").asInstanceOf[JSONArray] iterator()
			while (iterator.hasNext) {
				val o = (iterator next()).asInstanceOf[JSONObject]
				val yifyCache = new YIFYCache
				yifyCache.MovieID_:((o get "MovieID").asInstanceOf[String].toInt)
				yifyCache.MovieTitle_:((o get "MovieTitleClean").asInstanceOf[String])
				yifyCache.MovieYear_:((o get "MovieYear").asInstanceOf[String])
				yifyCache.MovieQuality_:((o get "Quality").asInstanceOf[String])
				yifyCache.MovieSize_:((o get "Size").asInstanceOf[String])
				yifyCache.MovieCoverImage_:((o get "CoverImage").asInstanceOf[String])

				if (!(DbControl ycQueryMovieID(yifyCache MovieID))) {
					status = DbControl ycInsertNewData yifyCache
				}
			}
		} catch {
			case jse: Exception =>
				LogWriter writeLog("Error parsing JSON from YIFY movie list", Level.ERROR)
				LogWriter writeLog(jse.getMessage, Level.ERROR)
		}
		status
	}
}