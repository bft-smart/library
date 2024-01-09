package bftsmart.tom.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.util.ServiceContent;

public interface IResponseSender {
	void sendResponseTo(MessageContext requestMsgCtx, ServiceContent response);
}