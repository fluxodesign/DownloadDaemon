package net.fluxo.dd.dbo

import java.util

/**
 * Created by viper on 27/03/14.
 * User: Ronald Kurniawan (viper)
 * Date: 27/03/14
 * Time: 5:47 PM
 */
class TPBPage {

	private val SearchResult = "TPB"

	private var _totalItems: Int = 0

	def TotalItems: Int = _totalItems
	def TotalItems_:(value: Int) { _totalItems = value }

	private var _tpbItems: util.ArrayList[TPBObject] = new util.ArrayList[TPBObject]

	def TPBItems: util.ArrayList[TPBObject] = _tpbItems
	def AddTPBItems(tpbo: TPBObject) { _tpbItems add tpbo }
	def TPBItems_:(value: util.ArrayList[TPBObject]) { _tpbItems = value }
}
