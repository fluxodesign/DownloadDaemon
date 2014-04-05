package net.fluxo.dd.dbo

import java.util

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/04/14
 * Time: 9:44 AM
 *
 */
class YIFYSearchResult {

	private var _movieCount: Int = 0

	def MovieCount: Int = _movieCount
	def MovieCount_:(value: Int) { _movieCount = value }

	private val _movieList: Option[util.List[MovieObject]] = Some(new util.ArrayList[MovieObject]())

	def MovieList: Option[util.List[MovieObject]] = _movieList
	def AddToMovieList(value: MovieObject) {
		_movieList getOrElse null add value
	}
}
