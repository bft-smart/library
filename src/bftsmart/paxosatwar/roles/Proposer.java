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
package bftsmart.paxosatwar.roles;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.paxosatwar.executionmanager.ExecutionManager;
import bftsmart.paxosatwar.messages.MessageFactory;
import bftsmart.reconfiguration.ServerViewManager;

/**
 * This class represents the proposer role in the consensus protocol.
 **/
public class Proposer {

    private ExecutionManager manager = null; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ServerCommunicationSystem communication; // Replicas comunication system
    private ServerViewManager reconfManager;

    /**
     * Creates a new instance of Proposer
     * 
     * @param communication Replicas communication system
     * @param factory Factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Proposer(ServerCommunicationSystem communication, MessageFactory factory,
            ServerViewManager manager) {
        this.communication = communication;
        this.factory = factory;
        this.reconfManager = manager;
    }

    /**
     * Sets the execution manager associated with this proposer
     * @param manager Execution manager
     */
    public void setManager(ExecutionManager manager) {
        this.manager = manager;
    }

    /**
     * This method is called by the TOMLayer (or any other)
     * to start the execution of one instance of the Paxos protocol.
     *
     * @param eid ID for the consensus instance to be started
     * @param value Value to be proposed
     */
    public void startExecution(int eid, byte[] value) {
        //******* EDUARDO BEGIN **************//
        communication.send(this.reconfManager.getCurrentViewAcceptors(),
                factory.createPropose(eid, 0, value, null));
        //******* EDUARDO END **************//
    }
}
