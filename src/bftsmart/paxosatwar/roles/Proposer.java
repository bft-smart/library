/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.paxosatwar.roles;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.paxosatwar.messages.MessageFactory;
import bftsmart.reconfiguration.ServerViewManager;

/**
 * This class represents the proposer role in the consensus protocol.
 **/
public class Proposer {

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
