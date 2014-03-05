package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/03/14
 * Time: 5:53 PM
 */
class Config {

	def isEmpty: Boolean = {
		var status = false
		if (GoogleAccount.isEmpty && GooglePassword.isEmpty && XMPPAccount.isEmpty && XMPPPassword.isEmpty && DownloadDir.isEmpty) {
			status = true
		}
		status
	}

	private var _rpcPort: Int = 6800

	def RPCPort: Int = _rpcPort
	def RPCPort_= (value: Int) {
		_rpcPort = value
	}

	private var _googleAccount: Option[String] = None

	def GoogleAccount: Option[String] = _googleAccount
	def GoogleAccount_= (value: String) {
		_googleAccount = Some(value)
	}

	private var _googlePassword: Option[String] = None

	def GooglePassword: Option[String] = _googlePassword
	def GooglePassword_= (value: String) {
		_googlePassword = Some(value)
	}

	private var _xmppAccount: Option[String] = None

	def XMPPAccount: Option[String] = _xmppAccount
	def XMPPAccount_= (value: String) {
		_xmppAccount = Some(value)
	}

	private var _xmppPassword: Option[String] = None

	def XMPPPassword: Option[String] = _xmppPassword
	def XMPPPassword_= (value: String) {
		_xmppPassword = Some(value)
	}

	private var _downloadDir: Option[String] = None

	def DownloadDir: Option[String] = _downloadDir
	def DownloadDir_= (value: String) {
		_downloadDir = Some(value)
	}
}
