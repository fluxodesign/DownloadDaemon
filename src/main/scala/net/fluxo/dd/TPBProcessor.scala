package net.fluxo.dd

import java.net.{URLEncoder, Socket}
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import net.fluxo.dd.dbo.{TPBDetails, TPBPage, TPBObject}
import scala.util.control.Breaks._
import com.google.gson.Gson
import java.util
import org.json.simple.{JSONValue, JSONObject}
import org.apache.commons.codec.net.URLCodec
import org.apache.log4j.Level

/**
 * User: Ronald Kurniawan (viper)
 * Date: 23/03/14
 * Time: 7:14 PM
 * Comment:
 */
class TPBProcessor {

	private val _url = "thepiratebay.se"
	private final val _httpUrl = "http://thepiratebay.se"
	private final val _searchUrl = "http://thepiratebay.se/search/[term]/[page]/99/[filter]"

	object TPBCats extends Enumeration {
		type Cat = Value
		val All = Value(0)
		val Audio = Value(100)
		val AudioMusic = Value(101)
		val AudioBooks = Value(102)
		val AudioSoundClips = Value(103)
		val AudioFLAC = Value(104)
		val AudioOther = Value(199)
		val Video = Value(200)
		val VideoMovies = Value(201)
		val VideoMoviesDVDR = Value(202)
		val VideoMusicVideos = Value(203)
		val VideoMovieClips = Value(204)
		val VideoTVShows = Value(205)
		val VideoHandheld = Value(206)
		val VideoHDMovies = Value(207)
		val VideoHDTVShows = Value(208)
		val Video3D = Value(209)
		val VideoOther = Value(299)
		val Applications = Value(300)
		val ApplicationsWindows = Value(301)
		val ApplicationsMac = Value(302)
		val ApplicationsUNIX = Value(303)
		val ApplicationsHandheld = Value(304)
		val ApplicationsIOS = Value(305)
		val ApplicationsAndroid = Value(306)
		val ApplicationsOther = Value(399)
		val Games = Value(400)
		val GamesPC = Value(401)
		val GamesMac = Value(402)
		val GamesPSX = Value(403)
		val GamesXBox360 = Value(404)
		val GamesWii = Value(405)
		val GamesHandheld = Value(406)
		val GamesIOS = Value(407)
		val GamesAndroid = Value(408)
		val GamesOther = Value(499)
		val Porn = Value(500)
		val PornMovies = Value(501)
		val PornMoviesDVDR = Value(502)
		val PornPictures = Value(503)
		val PornGames = Value(504)
		val PornHDMovies = Value(505)
		val PornMovieClips = Value(506)
		val PornOther = Value(599)
		val Other = Value(600)
		val OtherEbooks = Value(601)
		val OtherComics = Value(602)
		val OtherPictures = Value(603)
		val OtherCovers = Value(604)
		val OtherPhysibles = Value(605)
		val OtherOther = Value(699)
		def isValidCat(i: Int) = values.exists(_ == i)
	}

	def isSiteAlive: Boolean = {
		var reachable: Boolean = false
		var socket: Socket = null
		try {
			socket = new Socket(_url, 80)
			reachable = true
		} finally {
			if (socket != null) {
				try { socket.close() }
				catch {
					case ioe: IOException =>
				}
			}
		}
		reachable
	}

	def query(searchTerm: String, page: Int, cats: Array[Int]): String = {
		val sb = new StringBuilder
		var request = _searchUrl
		val encodedSearchTerm = URLEncoder encode (searchTerm, "UTF-8")
		request = request replaceAllLiterally ("[term]", encodedSearchTerm)
		request = request replaceAllLiterally ("[page]", page.toString)
		val categories = new StringBuilder
		for (x <- cats) {
			if (TPBCats.isValidCat(x)) categories append x append ","
		}
		if (categories endsWith ",") categories delete(categories.length - 1, categories.length)
		request = request replaceAllLiterally ("[filter]", categories toString())
		// make sure that tpb is active and hand it over to jsoup
		if (isSiteAlive) {
			val response = OUtils crawlServer request
			val document = Jsoup parse response
			val totalItems = queryTotalItemsFound(document)
			val itemList = parseItems(document)
			val tpbPage = new TPBPage
			tpbPage.TotalItems_:(totalItems)
			tpbPage.TPBItems_:(itemList)
			val iterator = tpbPage.TPBItems iterator()
			while (iterator.hasNext) {
				val obj = iterator.next
				obj.Info_:(queryDetails(obj.DetailsURL))
			}
			val gson = new Gson()
			sb.append(gson toJson tpbPage)
		}
		sb.toString()
	}

	def queryDetails(url: String): String = {
		//val sb = new StringBuilder
		var details = ""
		val encodedURL = new URLCodec encode url
		// DEBUG
		//LogWriter writeLog("ENCODED URL: " + encodedURL, Level.DEBUG)
		val response = OUtils crawlServer url
		val document = Jsoup parse response
		//val tpbd = new TPBDetails
		//tpbd.Request_:(URLEncoder encode (url, "UTF-8"))
		val info = parseDetails(document)
		try {
			val json = JSONValue.parseWithException(info).asInstanceOf[JSONObject]
			details = (json get "_info").asInstanceOf[String]
		}
		//tpbd.Info_:(URLEncoder encode (info, "UTF-8"))
		//val gson = new Gson()
		//sb.append(gson toJson tpbd)
		//sb toString()
		details
	}

	def queryTotalItemsFound(doc: Document): Int = {
		var ret: Int = 0
		val h2: Elements = doc getElementsByTag "h2"
		val iterator = h2 iterator()
		if (iterator.hasNext) {
			var strItemsFound = iterator next() text()
			if ((strItemsFound indexOf "(approx") > -1) {
				strItemsFound = strItemsFound substring(strItemsFound indexOf "(approx")
				strItemsFound = (strItemsFound replaceAll("\\D", "")).trim
				try {
					ret = strItemsFound.toInt
				} catch {
					case nfe: NumberFormatException =>
				}
			}
		}
		ret
	}

	def parseDetails(doc: Document): String = {
		var nfo = ""
		val div: Elements = doc select "div[class]"
		val iterator = div iterator()
		breakable {
			while (iterator.hasNext) {
				val d = iterator.next
				if ((d attr "class") equals "nfo") {
					val pre = d getElementsByTag "pre"
					if (pre.size() > 0) {
						nfo = (pre get 0) text()
						break()
					}
				}
			}
		}
		nfo
	}

	def parseItems(doc: Document): util.ArrayList[TPBObject] = {
		val list = new util.ArrayList[TPBObject]
		val tr: Elements = doc getElementsByTag "tr"
		val iterator = tr iterator()
		while (iterator.hasNext) {
			breakable {
				// descending to <td>s now..
				val td = iterator next()
				val tdIterator = td children() iterator()
				if (tdIterator.hasNext) {
					// first item deals with category...
					val item = tdIterator next()
					if (!(item tagName() equals "td")) break()
					val t = new TPBObject
					// first item, we need the category code...
					val linksIterator = item getElementsByTag "a" iterator()
					while (linksIterator.hasNext) {
						val cat = {
							try {
								(linksIterator next() attr "href" replaceAll("\\D", "")).toInt
							} catch {
								case nfe: NumberFormatException => 0
							}
						}
						if (cat > t.Type) t.Type_:(cat)
					}
					// second item deals with the URLs...
					val tdURL = tdIterator next()
					val urlIterator = tdURL getElementsByTag "a" iterator()
					while (urlIterator.hasNext) {
						val anchor = urlIterator next()
						if ((anchor attr "href" indexOf "magnet:") > -1) t.MagnetURL_:(anchor attr "href")
						else if ((anchor attr "href" indexOf ".torrent") > -1) t.TorrentURL_=("http:" + (anchor attr "href"))
						else if (anchor attr "class" equals "detLink") {
							t.DetailsURL_=(_httpUrl + (anchor attr "href"))
							t.Title_:(anchor text() trim)
						}
						else if (anchor attr "class" equals "detDesc") t.Uploader_=(anchor text())
					}
					val uploaderText = tdURL getElementsByTag "font" iterator()
					if (uploaderText.hasNext) {
						val infoText = uploaderText next() text()
						val arrText = infoText split ","
						for (s <- arrText) {
							val text = s.trim
							if (text startsWith "Uploaded") t.Uploaded_=(text replaceAllLiterally("Uploaded", "") trim)
							else if (text startsWith "Size") t.Size_=(text replaceAllLiterally("Size", "") trim)
						}
					}
					// third item is the seeder count
					val sc: Int = {
						if (tdIterator.hasNext){
							try { (tdIterator next() text()).toInt }
							catch {	case nfe: NumberFormatException => 0 }
						} else 0
					}
					t.Seeders_:(sc)
					// last item is the leecher count
					val lc: Int = {
						if (tdIterator.hasNext) {
							try { (tdIterator next() text()).toInt }
							catch { case nfe: NumberFormatException => 0 }
						} else 0
					}
					t.Leechers_:(lc)
					// add the "t" to ListBuffer
					list add t
				}
			}
		}
		list
	}
}

object TPBP extends TPBProcessor
