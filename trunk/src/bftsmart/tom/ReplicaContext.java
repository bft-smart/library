/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;

/**
 *
 * @author alysson
 */
public class ReplicaContext {
    
    private ServerCommunicationSystem cs; // Server side comunication system
    private ServerViewManager SVManager;

    public ReplicaContext(ServerCommunicationSystem cs, 
                                 ServerViewManager SVManager) {
        this.cs = cs;
        this.SVManager = SVManager;
    }
    
    //TODO: implement a method that allow the replica to send a message with
    //total order to all other replicas
       
    /**
     * Returns the static configuration of this replica.
     * 
     * @return the static configuration of this replica
     */
    public TOMConfiguration getStaticConfiguration() {
        return SVManager.getStaticConf();
    }
    
    /**
     * Returns the current view of the replica group.
     * 
     * @return the current view of the replica group.
     */
    public View getCurrentView() {
        return SVManager.getCurrentView();
    }

	public ServerCommunicationSystem getServerCommunicationSystem() {
		return cs;
	}

	public void setServerCommunicationSystem(ServerCommunicationSystem cs) {
		this.cs = cs;
	}
}
