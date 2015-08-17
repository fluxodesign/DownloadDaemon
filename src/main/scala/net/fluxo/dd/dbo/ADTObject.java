package net.fluxo.dd.dbo;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Ronald Kurniawan (viper)
 * @version 0.1, 14/08/15
 */
@XmlRootElement
public class ADTObject {
	String owner;
	String originalGid;
	String activeGid;
	long totalLength = -1;
	long completedLength = -1;
	String packageName;
	String infoHash;

	public ADTObject() {}

	@XmlElement
	public String getOwner() {
		return owner;
	}

	@XmlElement
	public String getOriginalGid() {
		return originalGid;
	}

	@XmlElement
	public String getActiveGid() {
		return activeGid;
	}

	@XmlElement
	public long getTotalLength() {
		return totalLength;
	}

	@XmlElement
	public long getCompletedLength() {
		return completedLength;
	}

	@XmlElement
	public String getPackageName() {
		return packageName;
	}

	@XmlElement
	public String getInfoHash() {
		return infoHash;
	}

	public boolean isOk() {
		return !getOwner().isEmpty() &&!getOriginalGid().isEmpty() && !getActiveGid().isEmpty() && getTotalLength() >= 0
			&& getCompletedLength() >= 0 &&	!getPackageName().isEmpty() && !getInfoHash().isEmpty();
	}
}
