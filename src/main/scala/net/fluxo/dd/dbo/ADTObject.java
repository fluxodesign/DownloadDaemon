package net.fluxo.dd.dbo;

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.1, 14/08/15
 */
public class ADTObject {
	String owner;
	String originalGid;
	String activeGid;
	long totalLength = -1;
	long completedLength = -1;
	String packageName;
	String infoHash;

	public ADTObject() {}

	public String getOwner() {
		return owner;
	}

	public String getOriginalGid() {
		return originalGid;
	}

	public String getActiveGid() {
		return activeGid;
	}

	public long getTotalLength() {
		return totalLength;
	}

	public long getCompletedLength() {
		return completedLength;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getInfoHash() {
		return infoHash;
	}

	public boolean isOk() {
		return !getOwner().isEmpty() &&!getOriginalGid().isEmpty() && !getActiveGid().isEmpty() && getTotalLength() >= 0
			&& getCompletedLength() >= 0 &&	!getPackageName().isEmpty() && !getInfoHash().isEmpty();
	}
}
