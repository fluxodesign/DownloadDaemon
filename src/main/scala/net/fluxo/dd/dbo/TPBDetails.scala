package net.fluxo.dd.dbo

/**
 * Created by Ronald Kurniawan (viper)
 * Date: 2/05/14 09:58AM
 * Comments:
 */
class TPBDetails {

	private val _id = "TPB_DETAILS"

	private var _info: String = ""

	def Info: String = { _info  }
	def Info_:(value: String) { _info = value }

	private var _request: String = ""

	def Request: String = { _request  }
	def Request_:(value: String) { _request = value }
}
