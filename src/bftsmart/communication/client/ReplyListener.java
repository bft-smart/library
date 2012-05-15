package bftsmart.communication.client;

import bftsmart.tom.core.messages.TOMMessage;


public interface ReplyListener {

	public void replyReceived(TOMMessage reply);
}
