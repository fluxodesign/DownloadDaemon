package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 10/03/14
 * Time: 7:56 PM
 */
class CountPackage {

	private var _count: Int = 0

	def CPCount: Int = _count
	def CPCount_= (value: Int) {
		_count = value
	}

	private var _package: Option[String] = None

	def CPPackage: Option[String] = _package
	def CPPackage_= (value: String) {
		_package = Some(value)
	}
}
