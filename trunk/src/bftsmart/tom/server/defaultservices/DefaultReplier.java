
package bftsmart.tom.server.defaultservices;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.server.Replier;

/**
 *
 * @author miguel
 */
public class DefaultReplier implements Replier{

    private ReplicaContext rc;
    
    @Override
    public void manageReply(TOMMessage request, MessageContext msgCtx) {
        rc.getServerCommunicationSystem().send(new int[]{request.getSender()}, request.reply);
    }

    @Override
    public void setReplicaContext(ReplicaContext rc) {
        this.rc = rc;
    }
    
}
