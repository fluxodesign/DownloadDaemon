package net.fluxo.dd.dbo

/**
 * User: Ronald Kurniawan (viper)
 * Date: 5/03/14
 * Time: 5:53 PM
 */
class Config {

	def isEmpty: Boolean = {
		var status = false
		if (XMPPAccount.isEmpty && XMPPPassword.isEmpty && XMPPProvider.isEmpty && DownloadDir.isEmpty) {
			status = true
		}
		status
	}

	private var _rpcPort: Int = 6800

	def RPCPort: Int = _rpcPort
	def RPCPort_= (value: Int) {
		_rpcPort = value
	}

	private var _rpcLimit: Int = 10

	def RPCLimit: Int = _rpcLimit
	def RPCLimit_=(value: Int) {
		_rpcLimit = value
	}

	private var _httpdPort: Int = 8080

	def HTTPDPort: Int = _httpdPort
	def HTTPDPort_= (value: Int) {
		_httpdPort = value
	}

	private var _xmppProvider: Option[String] = None

	def XMPPProvider: Option[String] = _xmppProvider
	def XMPPProvider_= (value: String) {
		if (!value.toLowerCase.equals("google") && !value.toLowerCase.equals("facebook")) {
			throw new IllegalArgumentException("Supported providers: Google and Facebook")
		}
		_xmppProvider = Some(value)
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
