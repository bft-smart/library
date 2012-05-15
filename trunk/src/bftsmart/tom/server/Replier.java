package bftsmart.tom.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.core.messages.TOMMessage;

/**
 *
 * @author miguel
 */
public interface Replier {
    
    public void setReplicaContext(ReplicaContext rc);

    public void manageReply(TOMMessage request, MessageContext msgCtx);

}

