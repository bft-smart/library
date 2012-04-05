
package navigators.smart.tom.server.defaultservices;

import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ReplicaContext;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.server.Replier;

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
