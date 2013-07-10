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


import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.paxosatwar.executionmanager.Execution;
import bftsmart.paxosatwar.executionmanager.ExecutionManager;
import bftsmart.paxosatwar.executionmanager.LeaderModule;
import bftsmart.paxosatwar.executionmanager.Round;
import bftsmart.paxosatwar.messages.MessageFactory;
import bftsmart.paxosatwar.messages.PaxosMessage;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.util.Logger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


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
    private ServerViewManager reconfManager;
    private Cipher cipher;

    /**
     * Creates a new instance of Acceptor.
     * @param communication Replicas communication system
     * @param factory Message factory for PaW messages
     * @param verifier Proof verifier
     * @param conf TOM configuration
     */
    public Acceptor(ServerCommunicationSystem communication, MessageFactory factory,
                                LeaderModule lm, ServerViewManager manager) {
        this.communication = communication;
        this.me = manager.getStaticConf().getProcessId();
        this.factory = factory;
        this.leaderModule = lm;
        this.reconfManager = manager;
        try {
            this.cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
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
        Round round = execution.getRound(msg.getRound(), reconfManager);
        switch (msg.getPaxosType()){
            case MessageFactory.PROPOSE:{
                    proposeReceived(round, msg);
            }break;
            case MessageFactory.WEAK:{
                    weakAcceptReceived(round, msg.getSender(), msg.getValue());
            }break;
            case MessageFactory.STRONG:{
                    strongAcceptReceived(round, msg);
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

            if (round.deserializedPropValue != null && !round.isWeakSetted(me)) {
                if(round.getExecution().getLearner().firstMessageProposed == null) {
                    round.getExecution().getLearner().firstMessageProposed = round.deserializedPropValue[0];
                }
                if (round.getExecution().getLearner().firstMessageProposed.consensusStartTime == 0) {
                    round.getExecution().getLearner().firstMessageProposed.consensusStartTime = consensusStartTime;
                    
                }
                round.getExecution().getLearner().firstMessageProposed.proposeReceivedTime = System.nanoTime();
                
                if(reconfManager.getStaticConf().isBFT()){
                    Logger.println("(Acceptor.executePropose) sending weak for " + eid);

                    round.setWeak(me, round.propValueHash);
                    round.getExecution().getLearner().firstMessageProposed.weakSentTime = System.nanoTime();
                    communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                            factory.createWeak(eid, round.getNumber(), round.propValueHash));

                    Logger.println("(Acceptor.executePropose) weak sent for " + eid);
                
                    computeWeak(eid, round, round.propValueHash);
                
                    Logger.println("(Acceptor.executePropose) weak computed for " + eid);
                
                } else {
                 	round.setStrong(me, round.propValueHash);
                 	round.getExecution().getLearner().firstMessageProposed.weakSentTime = System.nanoTime();
                        round.getExecution().getLearner().firstMessageProposed.strongSentTime = System.nanoTime();
                 	/**** LEADER CHANGE CODE! ******/
 	                round.getExecution().setQuorumWeaks(round.propValueHash);
 	                /*****************************************/

                        communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
 	                    factory.createStrong(eid, round.getNumber(), round.propValueHash));

                        computeStrong(eid, round, round.propValueHash);
                }
                executionManager.processOutOfContext(round.getExecution());
            }
        }
    }

    /**
     * Called when a WEAK message is received
     *
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void weakAcceptReceived(Round round, int a, byte[] value) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.weakAcceptReceived) WEAK from " + a + " for consensus " + eid);
        round.setWeak(a, value);

        computeWeak(eid, round, value);
    }

    /**
     * Computes weakly accepted values according to the standard PaW specification
     * (sends STRONG/DECIDE messages, according to the number of weakly accepted
     * values received).
     *
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeWeak(int eid, Round round, byte[] value) {
        int weakAccepted = round.countWeak(value);

        Logger.println("(Acceptor.computeWeak) I have " + weakAccepted +
                " weaks for " + eid + "," + round.getNumber());

        if (weakAccepted > reconfManager.getQuorumStrong() && Arrays.equals(value, round.propValueHash)) { // Can a send a STRONG message?
            if (!round.isStrongSetted(me)) {
                Logger.println("(Acceptor.computeWeak) sending STRONG for " + eid);

                /**** LEADER CHANGE CODE! ******/
                round.getExecution().setQuorumWeaks(value);
                /*****************************************/
                
                round.setStrong(me, value);

                if(round.getExecution().getLearner().firstMessageProposed!=null) {

                        round.getExecution().getLearner().firstMessageProposed.strongSentTime = System.nanoTime();
                }
                
                PaxosMessage pm = factory.createStrong(eid, round.getNumber(), value);

                // override default authentication and create a vector of MACs
                ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
                try {
                    new ObjectOutputStream(bOut).writeObject(pm);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }

                byte[] data = bOut.toByteArray();
        
                byte[] hash = tomLayer.computeHash(data);
                
                int[] processes = this.reconfManager.getCurrentViewAcceptors();
                
                HashMap<Integer, byte[]> macVector = new HashMap<Integer, byte[]>();
                
                for (int id : processes) {
                    try {
                        SecretKeySpec key = new SecretKeySpec(communication.getServersConn().getSecretKey(id).getEncoded(), "DES");
                        this.cipher.init(Cipher.ENCRYPT_MODE, key);
                        macVector.put(id, this.cipher.doFinal(hash));
                    } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException ex) {
                        ex.printStackTrace();
                    }
                }
                
                pm.setMACVector(macVector);
                
                int[] targets = this.reconfManager.getCurrentViewOtherAcceptors();
                communication.getServersConn().send(targets, pm, true);
                
                //communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                        //factory.createStrong(eid, round.getNumber(), value));
                round.addToProof(pm);
                computeStrong(eid, round, value);
            }
        }
    }

    /**
     * Called when a STRONG message is received
     * @param eid Execution ID of the received message
     * @param round Round of the receives message
     * @param a Replica that sent the message
     * @param value Value sent in the message
     */
    private void strongAcceptReceived(Round round, PaxosMessage msg) {
        int eid = round.getExecution().getId();
        Logger.println("(Acceptor.strongAcceptReceived) STRONG from " + msg.getSender() + " for consensus " + eid);
        round.setStrong(msg.getSender(), msg.getValue());
        round.addToProof(msg);

        computeStrong(eid, round, msg.getValue());
    }

    /**
     * Computes strongly accepted values according to the standard consensus
     * specification
     * @param round Round of the receives message
     * @param value Value sent in the message
     */
    private void computeStrong(int eid, Round round, byte[] value) {
        Logger.println("(Acceptor.computeStrong) I have " + round.countStrong(value) +
                " strongs for " + eid + "," + round.getNumber());

        if (round.countStrong(value) > reconfManager.getCertificateQuorum() && !round.getExecution().isDecided()) {
            Logger.println("(Acceptor.computeStrong) Deciding " + eid);
            decide(round, value);
        }
    }

    /**
     * This is the method invoked when a value is decided by this process
     * @param round Round at which the decision is made
     * @param value The decided value (got from WEAK or STRONG messages)
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
