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

package navigators.smart.paxosatwar.roles;

import java.security.SignedObject;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.messages.CollectProof;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.messages.Proof;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class represents the proposer role in the paxos protocol.
 **/
public class Proposer {

    private ExecutionManager manager = null; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ProofVerifier verifier; // Verifier for proofs
    private ServerCommunicationSystem communication; // Replicas comunication system
    private TOMConfiguration conf; // TOM configuration

    /**
     * Creates a new instance of Proposer
     * @param communication Replicas comunication system
     * @param factory Factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Proposer(ServerCommunicationSystem communication, MessageFactory factory,
            ProofVerifier verifier, TOMConfiguration conf) {
        this.communication = communication;
        this.verifier = verifier;
        this.factory = factory;
        this.conf = conf;
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
     * to start the execution of one instance of the paxos protocol.
     * 
     * @param eid ID for the consensus's execution to be started
     * @param value Value to be proposed
     */
    public void startExecution(long eid, byte[] value) {
        communication.send(manager.getAcceptors(),
                factory.createPropose(eid, 0, value, null));
    }

    /**
     * This method only deals with COLLECT messages.
     *
     * @param msg the COLLECT message received
     */
    public void deliver(PaxosMessage msg) {
        if (manager.checkLimits(msg)) {
            collectReceived(msg);
        }
    }

    /**
     * This method is executed when a COLLECT message is received.
     *
     * @param msg the collectReceived message
     */
    private void collectReceived(PaxosMessage<CollectProof> msg) {
        if(Logger.debug)
            Logger.println("(Proposer.collectReceived) COLLECT for "+
                         msg.getNumber()+","+msg.getRound()+" received.");

        Execution execution = manager.getExecution(msg.getNumber());
        execution.lock.lock();

        CollectProof cp =  msg.getProof();

        if (cp != null && verifier.validSignature(cp, msg.getSender())) {
//            CollectProof cp = null;
//            try {
//                cp = (CollectProof) proof.getObject();
//            } catch (Exception e) {
//                e.printStackTrace(System.out);
//            }

            if(Logger.debug)
                Logger.println("(Proposer.collectReceived) signed COLLECT for "+
                         msg.getNumber()+","+msg.getRound()+" received.");
            
            if ((cp != null) && (cp.getProofs(true) != null) &&
                    // the received proof (that the round was frozen) should be valid
                    verifier.validProof(execution.getId(), msg.getRound(), cp.getProofs(true)) &&
                    // this replica is the current leader
                    (cp.getLeader() == conf.getProcessId())) {

                int nextRoundNumber = msg.getRound() + 1;

                if(Logger.debug)
                    Logger.println("(Proposer.collectReceived) valid COLLECT for starting "+
                         execution.getId()+","+nextRoundNumber+" received.");

                Round round = execution.getRound(nextRoundNumber);
                
                round.setCollectProof(msg.getSender(),cp);

                if (verifier.countProofs(round.proofs) > manager.quorumStrong) {
                    if(Logger.debug)
                        Logger.println("(Proposer.collectReceived) proposing for "+
                            execution.getId()+","+nextRoundNumber);

                    byte[] inProp = verifier.getGoodValue(round.proofs, true);
                    byte[] nextProp = verifier.getGoodValue(round.proofs, false);

                    manager.getRequestHandler().imAmTheLeader();

                    communication.send(manager.getAcceptors(),
                            factory.createPropose(execution.getId(), nextRoundNumber,
                            inProp, new Proof(round.proofs, nextProp)));
                }
            }
        }

        execution.lock.unlock();
    }

    /* Not used in JBP, but can be usefull for systems in which there are processes
    that are only proposers

    private void paxosMessageReceived(int eid, int rid, int msgType,
    int sender, Object value) {
    Round round = manager.getExecution(eid).getRound(rid);
    if(msgType == WEAK) {
    round.setWeak(sender, value);
    if(round.countWeak(value) > manager.quorumFastDecide) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
    } else if(msgType == STRONG) {
    round.setStrong(sender, value);
    if(round.countStrong(value) > manager.quorum2F) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
    } else if(msgType == DECIDE) {
    round.setDecide(sender, value);
    if(round.countDecide(value) > manager.quorumF) {
    manager.getExecution(eid).decide(value,round.getNumber());
    }
     }
    }
     */
}
