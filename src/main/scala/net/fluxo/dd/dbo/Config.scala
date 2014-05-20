/*
 * Config.scala
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

/**
 * Data Object used as representation of application's configuration file.
 *
 * @author Ronald Kurniawan (viper)
 * @version 0.4.5, 5/03/14
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

	private var _sslKeystore: Option[String] = None

	def SSLKeystore: Option[String] = _sslKeystore

	def SSLKeystore_=(value: String) {
		_sslKeystore = Some(value)
	}

	private var _sslKeystorePassword: Option[String] = None

	def SSLKeystorePassword: Option[String] = _sslKeystorePassword

	def SSLKeystorePassword_=(value: String) {
		_sslKeystorePassword = Some(value)
	}

	private var _sslKeymanagerPassword: Option[String] = None

	def SSLKeymanagerPassword: Option[String] = _sslKeymanagerPassword

	def SSLKeymanagerPassword_=(value: String) {
		_sslKeymanagerPassword = Some(value)
	}
}
