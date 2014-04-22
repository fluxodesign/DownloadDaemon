package net.fluxo.dd

import org.quartz.{JobExecutionException, JobExecutionContext, Job}
import org.apache.log4j.Level
import java.util.concurrent.{TimeUnit, Future, Callable, Executors}
import org.json.simple.{JSONArray, JSONObject}
import org.json.simple.parser.JSONParser
import net.fluxo.dd.dbo.YIFYCache

/**
 * User: Ronald Kurniawan (viper)
 * Date: 3/04/14 3:04PM
 * Comment:
 */
class YIFYCacheJob extends Job {

	private val _yifyCacheService = Executors newFixedThreadPool 1

	@throws(classOf[JobExecutionException])
	override def execute(context: JobExecutionContext) {
		LogWriter.writeLog("Starting YIFY Cache Updater", Level.INFO)
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

class YCache extends Callable[String] {
	private var _pageNo = 0
	private var _totalPageNo = 0

	@throws(classOf[Exception])
	override def call(): String = {
		var totalItemsReported = 0L
		val jsonParser = new JSONParser
		var totalItemsInDb = DbControl ycQueryCount()
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

		totalItemsInDb = DbControl ycQueryCount()
		// if totalItemsInDb does not match total items returned by server then crawl the result
		//if (totalItemsInDb != totalItemsReported) crawlAndMatch(totalItemsReported, jsonParser)
		"OK"
	}

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
					status = false
					DbControl ycInsertNewData yifyCache
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