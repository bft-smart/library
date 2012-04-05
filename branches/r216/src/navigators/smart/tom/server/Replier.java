package navigators.smart.tom.server;

import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ReplicaContext;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 *
 * @author miguel
 */
public interface Replier {
    
    public void setReplicaContext(ReplicaContext rc);

    public void manageReply(TOMMessage request, MessageContext msgCtx);

}

