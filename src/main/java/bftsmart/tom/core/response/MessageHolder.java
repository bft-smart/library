package bftsmart.tom.core.response;

import bftsmart.tom.core.messages.TOMMessage;

public class MessageHolder {
	private final TOMMessage message;
	private final int receiver;
	private final boolean isHashed;

	public MessageHolder(TOMMessage message, int receiver, boolean isHashed) {
		this.message = message;
		this.receiver = receiver;
		this.isHashed = isHashed;
	}

	public TOMMessage getMessage() {
		return message;
	}

	public int getReceiver() {
		return receiver;
	}

	public boolean isHashed() {
		return isHashed;
	}
}
