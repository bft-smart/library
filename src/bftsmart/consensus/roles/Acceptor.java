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
package bftsmart.consensus.roles;


import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.server.ServerConnection;
import bftsmart.consensus.executionmanager.Execution;
import bftsmart.consensus.executionmanager.ExecutionManager;
import bftsmart.consensus.executionmanager.LeaderModule;
import bftsmart.consensus.Round;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.consensus.messages.PaxosMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.util.HashMap;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * This class represents the acceptor role in the consensus protocol.
 * This class work together with the TOMLayer class in order to
 * supply a atomic multicast service.
 *
 * @author Alysson Bessani
 */
public final class Acceptor {

    private int me; // This replica ID
    private ExecutionManager executionManager; // Execution manager of consensus's executions
    private MessageFactory factory; // Factory for PaW messages
    private ServerCommunicationSystem communication; // Replicas comunication system
    private LeaderModule leaderModule; // Manager for information about leaders
    private TOMLayer tomLayer; // TOM layer
    private ServerViewController controller;
    //private Cipher cipher;
    private Mac mac;

    /**
     * Creates a new instance of Acceptor.
     * @param communication Replicas communication system
     * @param factory Message factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Acceptor(ServerCommunicationSystem communication, MessageFactory factory,
                                LeaderModule lm, ServerViewController controller) {
        this.communication = communication;
        this.me = controller.getStaticConf().getProcessId();
        this.factory = factory;
        this.leaderModule = lm;
        this.controller = controller;
        try {
            //this.cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
            //this.cipher = Cipher.getInstance(ServerConnection.MAC_ALGORITHM);
            this.mac = Mac.getInstance(ServerConnection.MAC_ALGORITHM);
        } catch (NoSuchAlgorithmException /*| NoSuchPaddingException*/ ex) {
            ex.printStackTrace();
        }
    }

    public MessageFactory getFactory() {
        return factory;
    }

    /**
     * Sets the execution manager for this acceptor
     * @param manager Execution manager for this acceptor
     */
    public void setExecutionManager(ExecutionManager manager) {
        this.executionManager = manager;
    }

    /**
     * Sets the TOM layer for this acceptor
     * @param tom TOM layer for this acceptor
     */
    public void setTOMLayer(TOMLayer tom) {
        this.tomLayer = tom;
    }

    /**
     * Called by communication layer to delivery Paxos messages. This method
     * only verifies if the message can be executed and calls process message
     * (storing it on an out of context message buffer if this is not the case)
     *
     * @param msg Paxos messages delivered by the communication layer
     */
    public final void deliver(PaxosMessage msg) {
        if (executionManager.checkLimits(msg)) {
            Logger.println("processing paxos msg with id " + msg.getNumber());
            processMessage(msg);
        } else {
            Logger.println("out of context msg with id " + msg.getNumber());
            tomLayer.processOutOfContext();
        }
    }

    /**
     * Called when a Paxos message is received or when a out of context message must be processed.
     * It processes the received message according to its type
     *
     * @param msg The message to be processed
     */
    public final void processMessage(PaxosMessage msg) {
        Execution execution = executionManager.getExecution(msg.getNumber());

        execution.lock.lock();
        Round round = execution.getRound(msg.getRound(), controller);
        switch (msg.getPaxosType()){
            case MessageFactory.PROPOSE:{
                    proposeReceived(round, msg);
            }break;
            case MessageFactory.WRITE:{
                    writeReceived(round, msg.getSender(), msg.getValue());
            }break;
            case MessageFactory.ACCEPT:{
                    acceptReceived(round, msg);
            }
        }
        execution.lock.unlock();
    }

    /**
     * Called when a PROPOSE message is received or when processing a formerly out of context propose which
     * is know belongs to the current execution.
     *
     * @param msg The PROPOSE message to by processed
     */
    public void proposeReceived(Round round, PaxosMessage msg) {
        int eid = round.getExecution().getId();
    	Logger.println("(Acceptor.proposeReceived) PROPOSE for consensus " + eid);
    	if (msg.getSender() == leaderModule.getCurrentLeader()) {
    		executePropose(round, msg.getValue());
    	} else {
    		Logger.println("Propose received is not from the expected leader");
    	}
    }

    /**
     * Executes actions related to a proposed value.
     *
     * @param round the current round of the execution
     * @param value Value that is proposed
     */
    private void executePropose(Round round, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.executePropose) executing propose for " + eid + "," + round.getNumber());

        long consensusStartTime = System.nanoTime();

        
        if(round.propValue == null) { //only accept one propose per round
            round.propValue = value;
            round.propValueHash = tomLayer.computeHash(value);
            
            /*** LEADER CHANGE CODE ********/
            round.getExecution().addWritten(value);
            /*****************************************/

            //start this execution if it is not already running
            if (eid == tomLayer.getLastExec() + 1) {
                tomLayer.setInExec(eid);
            }
            round.deserializedPropValue = tomLayer.checkProposedValue(value, true);

            if (round.deserializedPropValue != null && !round.isWriteSetted(me)) {
                if(round.getExecution().getLearner().firstMessageProposed == null) {
                    round.getExecution().getLearner().firstMessageProposed = round.deserializedPropValue[0];
                }
                if (round.getExecution().getLearner().firstMessageProposed.consensusStartTime == 0) {
                    round.getExecution().getLearner().firstMessageProposed.consensusStartTime = consensusStartTime;
                    
                }
                round.getExecution().getLearner().firstMessageProposed.proposeReceivedTime = System.nanoTime();
                
                if(controller.getStaticConf().isBFT()){
                    Logger.println("(Acceptor.executePropose) sending WRITE for " + eid);

                    round.setWrite(me, round.propValueHash);
                    round.getExecution().getLearner().firstMessageProposed.writeSentTime = System.nanoTime();
                    communication.send(this.controller.getCurrentViewOtherAcceptors(),
                            factory.createWrite(eid, round.getNumber(), round.propValueHash));

                    Logger.println("(Acceptor.executePropose) WRITE sent for " + eid);
                
                    computeWrite(eid, round, round.propValueHash);
                
                    Logger.println("(Acceptor.executePropose) WRITE computed for " + eid);
                
                } else {
                 	round.setAccept(me, round.propValueHash);
                 	round.getExecution().getLearner().firstMessageProposed.writeSentTime = System.nanoTime();
                        round.getExecution().getLearner().firstMessageProposed.acceptSentTime = System.nanoTime();
                 	/**** LEADER CHANGE CODE! ******/
 	                round.getExecution().setQuorumWrites(round.propValueHash);
 	                /*****************************************/

                        communication.send(this.controller.getCurrentViewOtherAcceptors(),
 	                    factory.createAccept(eid, round.getNumber(), round.propValueHash));

                        computeAccept(eid, round, round.propValueHash);
                }
                executionManager.processOutOfContext(round.getExecution());
            }
        } 
    }

    /**
     * Called when a WRITE message is received
     *
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void writeReceived(Round round, int a, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.writeAcceptReceived) WRITE from " + a + " for consensus " + eid);
        round.setWrite(a, value);

        computeWrite(eid, round, value);
    }

    /**
     * Computes WRITE values according to Byzantine consensus specification
     * values received).
     *
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeWrite(int eid, Round round, byte[] value) {
        int writeAccepted = round.countWrite(value);
        
        Logger.println("(Acceptor.computeWrite) I have " + writeAccepted +
                " WRITEs for " + eid + "," + round.getNumber());

        if (writeAccepted > controller.getQuorumAccept() && Arrays.equals(value, round.propValueHash)) {
                        
            if (!round.isAcceptSetted(me)) {
                
                Logger.println("(Acceptor.computeWrite) sending WRITE for " + eid);

                /**** LEADER CHANGE CODE! ******/
                round.getExecution().setQuorumWrites(value);
                /*****************************************/
                
                round.setAccept(me, value);

                if(round.getExecution().getLearner().firstMessageProposed!=null) {

                        round.getExecution().getLearner().firstMessageProposed.acceptSentTime = System.nanoTime();
                }
                        
                PaxosMessage pm = factory.createAccept(eid, round.getNumber(), value);

                // override default authentication and create a vector of MACs
                ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
                try {
                    new ObjectOutputStream(bOut).writeObject(pm);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                byte[] data = bOut.toByteArray();
        
                //byte[] hash = tomLayer.computeHash(data);
                
                // check if consensus contains reconfiguration request
                TOMMessage [] msgs = round.deserializedPropValue;
                boolean hasReconf = false;

                for (TOMMessage msg : msgs) {
                    if (msg.getReqType() == TOMMessageType.RECONFIG
                            && msg.getViewID() == controller.getCurrentViewId()) {
                        hasReconf = true;
                    }
                }
                
                //If this consensus contains a reconfiguration request, we need to use
                // signatures...
                if (hasReconf) {
                    
                    PrivateKey RSAprivKey = controller.getStaticConf().getRSAPrivateKey();
                    
                    byte[] signature = TOMUtil.signMessage(RSAprivKey, data);
                                       
                    pm.setProof(signature);
                
                } else { //... if not, we can use MAC vectores
                    int[] processes = this.controller.getCurrentViewAcceptors();
                
                    HashMap<Integer, byte[]> macVector = new HashMap<Integer, byte[]>();
                
                    for (int id : processes) {
                        try {
                        
                            SecretKey key = null;
                            do {
                                key = communication.getServersConn().getSecretKey(id);
                                if (key == null) {
                                    System.out.println("I don't have yet a secret key with " + id + ". Retrying.");
                                    Thread.sleep(1000);
                                }

                            } while (key == null); // JCS: This loop is to solve a race condition where a
                                                   // replica might have already been insert in the view or
                                                   // recovered after a crash, but it still did not concluded
                                                   // the diffie helman protocol. Not an elegant solution,
                                                   // but for now it will do
                            this.mac.init(key);
                          macVector.put(id, this.mac.doFinal(data));
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        } catch (InvalidKeyException ex) {
                            
                           System.out.println("Problem with secret key from " + id);
                           ex.printStackTrace();
                        } 
                    }
                
                    pm.setProof(macVector);
                }
                
                int[] targets = this.controller.getCurrentViewOtherAcceptors();
                communication.getServersConn().send(targets, pm, true);
                
                //communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        //factory.createStrong(eid, round.getNumber(), value));
                round.addToProof(pm);
                computeAccept(eid, round, value);
            }
        }
    }

    /**
     * Called when a ACCEPT message is received
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void acceptReceived(Round round, PaxosMessage msg) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.acceptReceived) ACCEPT from " + msg.getSender() + " for consensus " + eid);
        round.setAccept(msg.getSender(), msg.getValue());
        round.addToProof(msg);

        computeAccept(eid, round, msg.getValue());
    }

    /**
     * Computes ACCEPT values according to the Byzantine consensus
     * specification
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeAccept(int eid, Round round, byte[] value) {
        Logger.println("(Acceptor.computeAccept) I have " + round.countAccept(value) +
                " ACCEPTs for " + eid + "," + round.getNumber());

        if (round.countAccept(value) > controller.getQuorumAccept() && !round.getExecution().isDecided()) {
            Logger.println("(Acceptor.computeAccept) Deciding " + eid);
            decide(round, value);
        }
    }

    /**
     * This is the method invoked when a value is decided by this process
     * @param round Round at which the decision is made
     * @param value The decided value (got from WRITE or ACCEPT messages)
     */
    private void decide(Round round, byte[] value) {        
        if (round.getExecution().getLearner().firstMessageProposed != null)
            round.getExecution().getLearner().firstMessageProposed.decisionTime = System.nanoTime();

        leaderModule.decided(round.getExecution().getId(),
                tomLayer.lm.getCurrentLeader()/*leaderModule.getLeader(round.getExecution().getId(),
                round.getNumber())*/);

        round.getExecution().decided(round, value);
    }
}
