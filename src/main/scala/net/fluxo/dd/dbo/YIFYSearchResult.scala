/*
 * YIFYSearchResult.scala
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
package net.fluxo.dd.dbo

import java.util

/**
 * Data Object for containing YIFY search results.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.4, 5/04/14
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
