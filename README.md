DownloadDaemon
==============

a Scala-based daemon utility that downloads media based on torrent and magnet URLs. 

This project is tied closely with BlackPearl, an Android client project that talks (sending "fetch" or "status" commands) to DownloadDaemon via REST web service commands. 

The daemon itself is essentially a monitor that can spawn aria2 instances to process the downloads. It queries the aria2c instances via the RPC capabilities that are built-in into aria2.