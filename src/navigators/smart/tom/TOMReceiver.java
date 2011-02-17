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
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.ShutdownThread;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class is used to
 * assemble a total order messaging layer
 *
 */
public abstract class TOMReceiver implements TOMRequestReceiver {

    private boolean tomStackCreated = false;

    /**
     * This metohd initializes the object
     * 
     * @param cs Server side comunication System
     * @param conf Total order messaging configuration
     */
    public void init(ServerCommunicationSystem cs, TOMConfiguration conf) {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }

        // Get group of replicas
        int[] group = new int[conf.getN()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }

        int me = conf.getProcessId(); // this process ID

        if (me >= group.length) {
            throw new RuntimeException("I'm not an acceptor!");
        }

        // Assemble the total order messaging layer
        MessageFactory messageFactory = new MessageFactory(me);
        ProofVerifier proofVerifier = new ProofVerifier(conf);
        LeaderModule lm = new LeaderModule();
        Acceptor acceptor = new Acceptor(cs, messageFactory, proofVerifier, lm, conf);

        Proposer proposer = new Proposer(cs, messageFactory, proofVerifier, conf);

        ExecutionManager manager = new ExecutionManager(acceptor, proposer,
                group, conf.getF(), me, conf.getFreezeInitialTimeout());

        acceptor.setManager(manager);
        proposer.setManager(manager);

        TOMLayer tomLayer = new TOMLayer(manager, this, lm, acceptor, cs, conf);
        manager.setTOMLayer(tomLayer);

        cs.setTOMLayer(tomLayer);
        cs.setRequestReceiver(tomLayer);

        acceptor.setTOMLayer(tomLayer);

        Runtime.getRuntime().addShutdownHook(new ShutdownThread(cs,lm,acceptor,manager,tomLayer));

        tomLayer.start(); // start the layer execution

        tomStackCreated = true;
    }
}

