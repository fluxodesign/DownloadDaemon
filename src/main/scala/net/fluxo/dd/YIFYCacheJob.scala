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
		_pageNo += 1
		var response = YIFYP procYIFYCache _pageNo
		if (_totalPageNo == 0 || _pageNo < _totalPageNo) {
			try {
				val jsonParser = new JSONParser
				val obj = (jsonParser parse response).asInstanceOf[JSONObject]
				if (_totalPageNo == 0) {
					val movieCount = (obj get "MovieCount").asInstanceOf[Int]
					_totalPageNo = movieCount / 50
					if (movieCount % 50 > 0) _totalPageNo += 1
				}
			} catch {
				case jse: Exception =>
					LogWriter writeLog("Error parsing JSON from YIFY movie list", Level.ERROR)
					LogWriter writeLog(jse.getMessage, Level.ERROR)
			}
		}
		var statusTrueCount = 0
		while (_pageNo < _totalPageNo && statusTrueCount < 3) {
			val status = processEntry(response)
			if (status) statusTrueCount += 1
			_pageNo += 1
			response = YIFYP procYIFYCache _pageNo
		}
		"OK"
	}

	private def processEntry(raw: String): Boolean = {
		var status = true
		try {
			val jsonParser = new JSONParser
			val obj = (jsonParser parse raw).asInstanceOf[JSONObject]
			val iterator = (obj get "MovieList").asInstanceOf[JSONArray] iterator()
			while (iterator.hasNext) {
				val o = (iterator next()).asInstanceOf[JSONObject]
				val yifyCache = new YIFYCache
				yifyCache.MovieID_:((o get "MovieID").asInstanceOf[Int])
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