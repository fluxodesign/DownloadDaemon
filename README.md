DownloadDaemon
==============

a Scala-based daemon utility that downloads media based on torrent and magnet URLs. 

This project is tied closely with BlackPearl, an Android client project that talks (sending "fetch" or "status" commands) to DownloadDaemon via REST web service commands. 

The daemon itself is essentially a monitor that can spawn aria2 instances to process the downloads. It queries the aria2c instances via the RPC capabilities that are built-in into aria2.

![Block Diagram of DownloadDaemon](https://lh3.googleusercontent.com/B7y2BhXrlmfH25T7CJi-XlKXusLE1HK0NHZNBkPgz_M=w292-h207-p-no)
**Block Diagram of DownloadDaemon**