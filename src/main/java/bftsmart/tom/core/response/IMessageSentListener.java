package bftsmart.tom.core.response;

import bftsmart.tom.core.messages.TOMMessage;

public interface IMessageSentListener {
    void messageSent(TOMMessage msg);
}
