package bftsmart.tom.util;

public class ServiceResponse {
	private int viewID;
	private final byte[] content;

	public ServiceResponse(byte[] content) {
		this.content = content;
	}

	public void setViewID(int viewID) {
		this.viewID = viewID;
	}

	public int getViewID() {
		return viewID;
	}

	public byte[] getContent() {
		return content;
	}
}
