package bftsmart.tom.util;

/**
 * This class is used to store the response from each replica.
 *
 * @author robin
 */
public class ServiceContent {
	// The common content is the same for all replicas.
	private byte[] commonContent;

	// The replica specific content is different for each replica.
	private byte[] replicaSpecificContent;

	public ServiceContent(byte[] commonContent) {
		this(commonContent, null);
	}

	public ServiceContent(byte[] commonContent, byte[] replicaSpecificContent) {
		this.commonContent = commonContent;
		this.replicaSpecificContent = replicaSpecificContent;
	}

	public byte[] getCommonContent() {
		return commonContent;
	}

	public byte[] getReplicaSpecificContent() {
		return replicaSpecificContent;
	}

	public void setReplicaSpecificContent(byte[] replicaSpecificContent) {
		this.replicaSpecificContent = replicaSpecificContent;
	}

	public void setCommonContent(byte[] commonContent) {
		this.commonContent = commonContent;
	}
}
