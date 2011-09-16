/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.reconfiguration.View;
import navigators.smart.reconfiguration.util.TOMConfiguration;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 *
 * @author alysson
 */
public class ReplicaContext {
    
    private ServerCommunicationSystem cs; // Server side comunication system
    private ReconfigurationManager reconfManager;

    public ReplicaContext(ServerCommunicationSystem cs, 
                                 ReconfigurationManager reconfManager) {
        this.cs = cs;
        this.reconfManager = reconfManager;
    }
    
    //TODO: implement a method that allow the replica to send a message with
    //total order to all other replicas
    
    /**
     * send a message to one or more clients.
     * 
     * @param targets the target receivers of the message
     * @param msg the message to be sent
     */
    public void sendMessage(int[] targets, TOMMessage msg) {
        cs.send(targets, msg);
    }
    
    /**
     * Returns the static configuration of this replica.
     * 
     * @return the static configuration of this replica
     */
    public TOMConfiguration getStaticConfiguration() {
        return reconfManager.getStaticConf();
    }
    
    /**
     * Returns the current view of the replica group.
     * 
     * @return the current view of the replica group.
     */
    public View getCurrentView() {
        return reconfManager.getCurrentView();
    }
}
