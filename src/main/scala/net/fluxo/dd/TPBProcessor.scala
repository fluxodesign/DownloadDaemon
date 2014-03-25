package net.fluxo.dd

import java.net.Socket
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements

/**
 * User: Ronald Kurniawan (viper)
 * Date: 23/03/14
 * Time: 7:14 PM
 * Comment:
 */
class TPBProcessor {

	private val _url = "thepiratebay.se"
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
		request = request replaceAllLiterally ("[term]", searchTerm)
		request = request replaceAllLiterally ("[page]", page.toString)
		val categories = new StringBuilder
		for (x <- cats) {
			if (TPBCats.isValidCat(x)) categories append x append ","
		}
		if (categories endsWith ",") categories delete(categories.length - 1, categories.length)
		request = request replaceAllLiterally ("[filter]", categories toString())
		// make sure that tpb is active and hand it over to jsoup
		if (isSiteAlive) {
			val jsoup = Jsoup connect request userAgent "Fluxo-DD/1.0" get()
			sb.append(queryTotalItemsFound(jsoup))
		}
		sb.toString()
	}

	def queryTotalItemsFound(doc: Document): String = {
		var ret: Option[String] = None
		val h2: Elements = doc getElementsByTag "h2"
		while ((h2 iterator()).hasNext) {
			val e = h2 iterator() next() children()
			while ((e iterator()).hasNext) {
				val elem = e iterator() next()
				elem tagName() match {
					case "span" =>
					case _ =>
						ret = Some(elem text())
				}
			}
		}
		ret getOrElse ""
	}
}

object TPBP extends TPBProcessor