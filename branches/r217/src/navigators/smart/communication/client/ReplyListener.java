package navigators.smart.communication.client;

import navigators.smart.tom.core.messages.TOMMessage;


public interface ReplyListener {

	public void replyReceived(TOMMessage reply);
}
