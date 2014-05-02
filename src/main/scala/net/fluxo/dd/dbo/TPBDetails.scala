package net.fluxo.dd.dbo

/**
 * Created by Ronald Kurniawan (viper)
 * Date: 2/05/14 09:58AM
 * Comments:
 */
class TPBDetails {

	private var _info: Option[String] = None

	def Info: String = { _info getOrElse "" }
	def Info_:(value: String) { _info = Some(value) }

	private var _request: Option[String] = None

	def Request: String = { _request getOrElse "" }
	def Request_:(value: String) { _request = Some(value) }
}
