package net.fluxo.dd

import java.net.{MalformedURLException, URL, HttpURLConnection}
import org.apache.log4j.Level
import java.io.{InputStreamReader, BufferedReader, IOException}
import org.jsoup.Jsoup

/**
 * User: viper
 * Date: 11/03/14
 * Time: 10:06 AM
 *
 */
class YIFYProcessor {

	/*
		quality: 0 = ALL, 1 = 720p, 2 = 1080p, 3 = 3D
		page: page to request
        rating: minimum rating to request, 0 - 9, default 0 (ALL)
	 */
	def procListMovie(page: Int, quality:Int, rating: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/list.json?limit=15")
		val response = new StringBuilder
		if (quality <= 3 && quality >= 0) request.append("&quality=").append(quality  match {
			case 0 => "ALL"
			case 1 => "720p"
			case 2 => "1080p"
			case 3 => "3D"
		})
		if (page > 0) request.append("&set=" + page)
		if (rating >= 0 && rating <= 9) request.append("&rating=" + rating)
		// send the request...
		try {
			val doc = Jsoup.connect(request.toString())
			response.append(doc.get().html())
			/*val httpConn = url.openConnection().asInstanceOf[HttpURLConnection]
			val br = new BufferedReader(new InputStreamReader(httpConn.getInputStream))
			var line: String = null
			while ((line = br.readLine()) != null) {
				// DEBUG
				System.out.println("REPLY: " + line)
				response.append(line)
			}
			if (response.toString().length == 0) response append "EMPTY"
			httpConn.disconnect()*/
		} catch {
			case mue: MalformedURLException =>
				LogWriter.writeLog("URL " + request.toString() + " is malformed", Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(mue), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
		response.toString()
	}

	def procMovieDetails(id: Int): String = {
		val request: StringBuilder = new StringBuilder("http://yts.re/api/movie.json?id=").append(id)
		val response = new StringBuilder
		try {
			val doc = Jsoup.connect(request.toString())
			response.append(doc.get().html())
			/*val url = new URL(request.toString())
			val httpConn = url.openConnection().asInstanceOf[HttpURLConnection]
			val br = new BufferedReader(new InputStreamReader(httpConn.getInputStream))
			var line: String = null
			while (line = br readLine()) != null) {
				response append line
			}
			httpConn disconnect()*/
		} catch {
			case mue: MalformedURLException =>
				LogWriter.writeLog("URL " + request.toString() + " is malformed", Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(mue), Level.ERROR)
			case ioe: IOException =>
				LogWriter.writeLog("IO/E: " + ioe.getMessage, Level.ERROR)
				LogWriter.writeLog(LogWriter.stackTraceToString(ioe), Level.ERROR)
		}
		response toString()
	}
}

object YIFYP extends YIFYProcessor