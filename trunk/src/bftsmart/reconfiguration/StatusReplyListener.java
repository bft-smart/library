package bftsmart.reconfiguration;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;

public class StatusReplyListener implements ReplyListener {
    private StatusReply response;
    private Semaphore sm;

    public StatusReplyListener() {
    	sm = new Semaphore(0);
    }
    
    public StatusReply getResponse() {
    	if(response != null)
           	return response;
        try {
            if (!this.sm.tryAcquire(20, TimeUnit.SECONDS)) {
                Logger.println("----StatusReplyListener ########TIMEOUT########");
                return null;
            }
        } catch (InterruptedException ex) {}
        Logger.println("Response extracted = " + response);
       	return response;
    }

    @Override
	public void replyReceived(TOMMessage reply) {
        System.out.println("Receiving reply from " + reply.getSender() + " with reqId:" + reply.getSequence());
        String tmpReply = (String)TOMUtil.getObject(reply.getContent());
        response = StatusReply.fromString(tmpReply);
        this.sm.release();
	}

}
