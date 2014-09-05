DownloadDaemon
==============

a Scala-based daemon utility that downloads media based on torrent and magnet URLs. 

This project is tied closely with BlackPearl, an Android client project that talks (sending "fetch" or "status" commands) to DownloadDaemon via REST web service commands. 

The daemon itself is essentially a monitor that can spawn aria2 instances to process the downloads. It queries the aria2c instances via the RPC capabilities that are built-in into aria2.

![Block Diagram of DownloadDaemon](https://lh3.googleusercontent.com/B7y2BhXrlmfH25T7CJi-XlKXusLE1HK0NHZNBkPgz_M=w292-h207-p-no)  
**Block Diagram of DownloadDaemon**

The daemon uses [H2 Database](http://www.h2database.com) to store its data, including cache and download request details. We use H2 db because of its compact size and the fact that it is embeddable into our daemon.

The daemon consists of several parts working together in the background:

* HTTP Daemon  
* XMPP Monitor  
* Cache Monitor  
* Download Monitor  

### HTTP Daemon   

This part serves the REST web service methods for the client. We use embedded Jetty server to actually serve the REST methods. It is a much more elegant option than coding up our own application server.

### XMPP Monitor ###

Beside the REST methods, we can also send commands to the daemon via Facebook chat and Google chat. To achieve this, we use [Ignite Realtime's Smack API](www.igniterealtime.org/projects/smack) to implement XMPP chat client. 

### Cache Monitor ###

The cache monitor consumes the web service from YIFY site every X hours to check whether there are any new movies being uploaded. Data for new movies is saved into the database to enable the Android client to perform title-based search. We implemented this design because there are no search capabilities available from YIFY.

### Download Monitor ###

Whenever there is a download happening, this monitor checks the download status every X seconds, by sending RPC message to the aria2 instance that handles the download. The monitor then saves the returned data into the database so that in the case the daemon crashes and get restarted later, it knows where to pick up the download from.