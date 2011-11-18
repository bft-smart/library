/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.ShutdownHookThread;

/**
 * This class is used to
 * assemble a total order messaging layer
 *
 */
public abstract class TOMReceiver implements TOMRequestReceiver {

    private boolean tomStackCreated = false;
    private ReplicaContext replicaCtx = null;

    public void init(ServerCommunicationSystem cs, ServerViewManager reconfManager) {
        this.init(cs, reconfManager,-1,-1);
    }
    
    /**
     * This method initializes the object
     * 
     * @param cs Server side communication System
     * @param conf Total order messaging configuration
     */
    public void init(ServerCommunicationSystem cs, ServerViewManager reconfManager, 
                          int lastExec, int lastLeader) {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }
       
        //******* EDUARDO BEGIN **************//
        int me = reconfManager.getStaticConf().getProcessId(); // this process ID

        if (!reconfManager.isInCurrentView()) {
            throw new RuntimeException("I'm not an acceptor!");
        }
        //******* EDUARDO END **************//
        
        // Assemble the total order messaging layer
        MessageFactory messageFactory = new MessageFactory(me);
        
        LeaderModule lm = new LeaderModule(reconfManager);
                
        Acceptor acceptor = new Acceptor(cs, messageFactory, lm, reconfManager);
        cs.setAcceptor(acceptor);
        
        Proposer proposer = new Proposer(cs, messageFactory, reconfManager);

        ExecutionManager manager = new ExecutionManager(reconfManager, acceptor, proposer,
                               me);
        
        acceptor.setManager(manager);
        proposer.setManager(manager);

        TOMLayer tomLayer = new TOMLayer(manager, this, lm, acceptor, cs, reconfManager);
        
        manager.setTOMLayer(tomLayer);
        
        //******* EDUARDO BEGIN **************//
        reconfManager.setTomLayer(tomLayer);
        //******* EDUARDO END **************//
        
        cs.setTOMLayer(tomLayer);
        cs.setRequestReceiver(tomLayer);

        acceptor.setTOMLayer(tomLayer);

        if(reconfManager.getStaticConf().isShutdownHookEnabled()){
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(cs,lm,acceptor,manager,tomLayer));
        }
        tomLayer.start(); // start the layer execution
        tomStackCreated = true;
        
        replicaCtx = new ReplicaContext(cs, reconfManager);
    }

    /**
     * Obtains the current replica context (getting access to several information
     * and capabilities of the replication engine).
     * 
     * @return this replica context
     */
    public final ReplicaContext getReplicaContext() {
        return replicaCtx;
    }
}
