/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.core;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.Decision;
import bftsmart.consensus.Epoch;
import bftsmart.consensus.Consensus;
import bftsmart.consensus.TimestampValuePair;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.leaderchange.RequestsTimer;
import bftsmart.tom.leaderchange.CollectData;
import bftsmart.tom.leaderchange.LCManager;
import bftsmart.tom.leaderchange.LCMessage;
import bftsmart.tom.leaderchange.LastEidData;
import bftsmart.tom.util.BatchBuilder;
import bftsmart.tom.util.BatchReader;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.SignedObject;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.codec.binary.Base64;

/**
 *
 * This class implements the synchronization phase described in
 * Joao Sousa's 'From Byzantine Consensus to BFT state machine replication: a latency-optimal transformation' (May 2012)
 * 
 * This class implements all optimizations described at the end of the paper
 * 
 * @author joao
 */
public class Synchronizer {

    // out of context messages related to the leader change are stored here
    private final HashSet<LCMessage> outOfContextLC;

    // Manager of the leader change
    private final LCManager lcManager;
    
    //Total order layer
    private final TOMLayer tom;
    
    // Stuff from TOMLayer that this object needs
    private final RequestsTimer requestsTimer;
    private final ExecutionManager execManager;
    private final ServerViewController controller;
    private final BatchBuilder bb;
    private final ServerCommunicationSystem communication;
    private final LeaderModule lm;
    private final StateManager stateManager;
    private final Acceptor acceptor;
    private final MessageDigest md;
            
    // Attributes to temporarely store synchronization info
    // if state transfer is required for synchronization
    private int tempRegency = -1;
    private LastEidData tempLastHighestEid = null;
    private int tempCurrentEid = -1;
    private HashSet<SignedObject> tempSignedCollects = null;
    private byte[] tempPropose = null;
    private int tempBatchSize = -1;
    private boolean tempIAmLeader = false;

    
    public Synchronizer(TOMLayer tom) {
        
        this.tom = tom;
        
        this.requestsTimer = this.tom.requestsTimer;
        this.execManager = this.tom.execManager;
        this.controller = this.tom.controller;
        this.bb = this.tom.bb;
        this.communication = this.tom.getCommunication();
        this.lm = this.tom.lm;
        this.stateManager = this.tom.stateManager;
        this.acceptor = this.tom.acceptor;
        this.md = this.tom.md;
        
        this.outOfContextLC = new HashSet<>();
	this.lcManager = new LCManager(this.tom,this.controller, this.md);
    }

    public LCManager getLCManager() {
        return lcManager;
    }
    
    /**
     * This method is called when there is a timeout and the request has already
     * been forwarded to the leader
     *
     * @param requestList List of requests that the replica wanted to order but
     * didn't manage to
     */
    public void triggerTimeout(List<TOMMessage> requestList) {

        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int regency = lcManager.getNextReg();
        
        requestsTimer.stopTimer();
        requestsTimer.Enabled(false);

	// still not in the leader change phase?
        if (lcManager.getNextReg() == lcManager.getLastReg()) {

            Logger.println("(Synchronizer.triggerTimeout) initialize synchronization phase");

            lcManager.setNextReg(lcManager.getLastReg() + 1); // define next timestamp

            regency = lcManager.getNextReg(); // update variable 

            // store messages to be ordered
            lcManager.setCurrentRequestTimedOut(requestList);

            // store information about messages that I'm going to send
            lcManager.addStop(regency, this.controller.getStaticConf().getProcessId());

            execManager.stop(); // stop consensus execution

            //Get requests that timed out and the requests received in STOP messages
            //and add those STOPed requests to the client manager
            addSTOPedRequestsToClientManager();
            List<TOMMessage> messages = getRequestsToRelay();

            try { // serialize content to send in STOP message
                out = new ObjectOutputStream(bos);

                if (messages != null && messages.size() > 0) {

					//TODO: If this is null, then there was no timeout nor STOP messages.
                    //What to do?
                    byte[] serialized = bb.makeBatch(messages, 0, 0, controller);
                    out.writeBoolean(true);
                    out.writeObject(serialized);
                } else {
                    out.writeBoolean(false);
                    System.out.println("(Synchronizer.triggerTimeout) Strange... did not include any request in my STOP message for regency " + regency);
                }

                byte[] payload = bos.toByteArray();

                out.flush();
                bos.flush();

                out.close();
                bos.close();

                // send STOP-message
                System.out.println("(Synchronizer.triggerTimeout) sending STOP message to install regency " + regency + " with " + (messages != null ? messages.size() : 0) + " request(s) to relay");
                communication.send(this.controller.getCurrentViewOtherAcceptors(),
                        new LCMessage(this.controller.getStaticConf().getProcessId(), TOMUtil.STOP, regency, payload));

            } catch (IOException ex) {
                ex.printStackTrace();
                java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    out.close();
                    bos.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        processOutOfContextSTOPs(regency); // the replica might have received STOPs
                                           // that were out of context at the time they
                                           // were received, but now can be processed
        
        startSynchronization(regency); // evaluate STOP messages

    }

    // Processes STOP messages that were not process upon reception, because they were
    // ahead of the replica's expected regency
    private void processOutOfContextSTOPs(int regency) {

        Logger.println("(Synchronizer.processOutOfContextSTOPs) Checking if there are out of context STOPs for regency " + regency);

        Set<LCMessage> stops = getOutOfContextLC(TOMUtil.STOP, regency);

        if (stops.size() > 0) {
            System.out.println("(Synchronizer.processOutOfContextSTOPs) Processing " + stops.size() + " out of context STOPs for regency " + regency);
        } else {
            Logger.println("(Synchronizer.processOutOfContextSTOPs) No out of context STOPs for regency " + regency);
        }

        for (LCMessage m : stops) {
            TOMMessage[] requests = deserializeTOMMessages(m.getPayload());

            // store requests that came with the STOP message
            lcManager.addRequestsFromSTOP(requests);

            // store information about the STOP message
            lcManager.addStop(regency, m.getSender());
        }
    }

    // Processes STOPDATA messages that were not process upon reception, because they were
    // ahead of the replica's expected regency
    private void processSTOPDATA(LCMessage msg, int regency) {

        //TODO: It is necessary to verify the proof of the last decided consensus and the signature of the state of the current consensus!
        LastEidData lastData = null;
        SignedObject signedCollect = null;

        int last = -1;
        byte[] lastValue = null;
        Set<ConsensusMessage> proof = null;

        ByteArrayInputStream bis;
        ObjectInputStream ois;

        try { // deserialize the content of the message

            bis = new ByteArrayInputStream(msg.getPayload());
            ois = new ObjectInputStream(bis);

            if (ois.readBoolean()) { // content of the last decided cid

                last = ois.readInt();

                lastValue = (byte[]) ois.readObject();
                proof = (Set<ConsensusMessage>) ois.readObject();

                //TODO: Proof is missing!
            }

            lastData = new LastEidData(msg.getSender(), last, lastValue, proof);

            lcManager.addLastEid(regency, lastData);

            signedCollect = (SignedObject) ois.readObject();

            ois.close();
            bis.close();

            lcManager.addCollect(regency, signedCollect);

            int bizantineQuorum = (controller.getCurrentViewN() + controller.getCurrentViewF()) / 2;
            int cftQuorum = (controller.getCurrentViewN()) / 2;

            // Did I already got messages from a Byzantine/Crash quorum,
            // related to the last cid as well as for the current?
            boolean conditionBFT = (controller.getStaticConf().isBFT() && lcManager.getLastEidsSize(regency) > bizantineQuorum
                    && lcManager.getCollectsSize(regency) > bizantineQuorum);

            boolean conditionCFT = (lcManager.getLastEidsSize(regency) > cftQuorum && lcManager.getCollectsSize(regency) > cftQuorum);

            if (conditionBFT || conditionCFT) {
                catch_up(regency);
            }

        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace(System.err);
        }

    }

    // Processes SYNC messages that were not process upon reception, because they were
    // ahead of the replica's expected regency
    private void processSYNC(byte[] payload, int regency) {

        LastEidData lastHighestEid = null;
        int currentEid = -1;
        HashSet<SignedObject> signedCollects = null;
        byte[] propose = null;
        int batchSize = -1;

        ByteArrayInputStream bis;
        ObjectInputStream ois;

        try { // deserialization of the message content

            bis = new ByteArrayInputStream(payload);
            ois = new ObjectInputStream(bis);

            lastHighestEid = (LastEidData) ois.readObject();
            currentEid = ois.readInt();
            signedCollects = (HashSet<SignedObject>) ois.readObject();
            propose = (byte[]) ois.readObject();
            batchSize = ois.readInt();

            lcManager.setCollects(regency, signedCollects);

            // Is the predicate "sound" true? Is the certificate for LastEid valid?
            if (lcManager.sound(lcManager.selectCollects(regency, currentEid)) && (!controller.getStaticConf().isBFT() || lcManager.hasValidProof(lastHighestEid))) {

                finalise(regency, lastHighestEid, currentEid, signedCollects, propose, batchSize, false);
            }

            ois.close();
            bis.close();

        } catch (IOException ex) {
            ex.printStackTrace();
            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);

        }
    }

    // Fetches synchronization messages that were not process upon reception,
    // because they were ahead of the replica's expected regency
    private Set<LCMessage> getOutOfContextLC(int type, int regency) {

        HashSet<LCMessage> result = new HashSet<>();

        for (LCMessage m : outOfContextLC) {

            if (m.getType() == type && m.getReg() == regency) {
                result.add(m);
            }

        }

        outOfContextLC.removeAll(result); // avoid memory leaks

        return result;
    }

    // Deserializes requests that were included in STOP messages
    private TOMMessage[] deserializeTOMMessages(byte[] playload) {

        ByteArrayInputStream bis;
        ObjectInputStream ois;

        TOMMessage[] requests = null;

        try { // deserialize the content of the STOP message

            bis = new ByteArrayInputStream(playload);
            ois = new ObjectInputStream(bis);

            boolean hasReqs = ois.readBoolean();

            if (hasReqs) {

                // Store requests that the other replica did not manage to order
                //TODO: The requests have to be verified!
                byte[] temp = (byte[]) ois.readObject();
                BatchReader batchReader = new BatchReader(temp,
                        controller.getStaticConf().getUseSignatures() == 1);
                requests = batchReader.deserialiseRequests(controller);
            }

            ois.close();
            bis.close();

        } catch (IOException ex) {
            ex.printStackTrace();
            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            ex.printStackTrace();
            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);

        }

        return requests;

    }

    // Get requests that timed out and the requests received in STOP messages
    private List<TOMMessage> getRequestsToRelay() {

        List<TOMMessage> messages = lcManager.getCurrentRequestTimedOut();

        if (messages == null) {

            messages = new LinkedList<>();
        }

        // Include requests from STOP messages in my own STOP message
        List<TOMMessage> messagesFromSTOP = lcManager.getRequestsFromSTOP();
        if (messagesFromSTOP != null) {

            for (TOMMessage m : messagesFromSTOP) {

                if (!messages.contains(m)) {

                    messages.add(m);
                }
            }
        }

        Logger.println("(Synchronizer.getRequestsToRelay) I need to relay " + messages.size() + " requests");

        return messages;
    }

    //adds requests received via STOP messages to the client manager
    private void addSTOPedRequestsToClientManager() {

        List<TOMMessage> messagesFromSTOP = lcManager.getRequestsFromSTOP();
        if (messagesFromSTOP != null) {

            Logger.println("(Synchronizer.addRequestsToClientManager) Adding to client manager the requests contained in STOP messages");

            for (TOMMessage m : messagesFromSTOP) {
                tom.requestReceived(m);

            }
        }

    }

    // this method is called when a timeout occurs or when a STOP message is recevied
    private void startSynchronization(int nextReg) {

        boolean enterFirstPhase = this.controller.getStaticConf().isBFT();
        boolean condition = false;
        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        // pass to the leader change phase if more than f messages have been received already
        if (enterFirstPhase && lcManager.getStopsSize(nextReg) > this.controller.getCurrentViewF() && lcManager.getNextReg() == lcManager.getLastReg()) {

            Logger.println("(Synchronizer.startSynchronization) initialize synch phase");
            requestsTimer.Enabled(false);
            requestsTimer.stopTimer();

            lcManager.setNextReg(lcManager.getLastReg() + 1); // define next timestamp

            int regency = lcManager.getNextReg();

            // store information about message I am going to send
            lcManager.addStop(regency, this.controller.getStaticConf().getProcessId());

            execManager.stop(); // stop execution of consensus

            //Get requests that timed out and the requests received in STOP messages
            //and add those STOPed requests to the client manager
            addSTOPedRequestsToClientManager();
            List<TOMMessage> messages = getRequestsToRelay();

            try { // serialize conent to send in the STOP message
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                // Do I have messages to send in the STOP message?
                if (messages != null && messages.size() > 0) {

                    //TODO: If this is null, there was no timeout nor STOP messages.
                    //What shall be done then?
                    out.writeBoolean(true);
                    byte[] serialized = bb.makeBatch(messages, 0, 0, controller);
                    out.writeObject(serialized);
                } else {
                    out.writeBoolean(false);
                    System.out.println("(Synchronizer.startSynchronization) Strange... did not include any request in my STOP message for regency " + regency);
                }

                out.flush();
                bos.flush();

                byte[] payload = bos.toByteArray();
                out.close();
                bos.close();

                // send message STOP
                System.out.println("(Synchronizer.startSynchronization) sending STOP message to install regency " + regency + " with " + (messages != null ? messages.size() : 0) + " request(s) to relay");
                communication.send(this.controller.getCurrentViewOtherAcceptors(),
                        new LCMessage(this.controller.getStaticConf().getProcessId(), TOMUtil.STOP, regency, payload));

            } catch (IOException ex) {
                ex.printStackTrace();
                java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    out.close();
                    bos.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

        if (this.controller.getStaticConf().isBFT()) {
            condition = lcManager.getStopsSize(nextReg) > (2 * this.controller.getCurrentViewF()) && lcManager.getNextReg() > lcManager.getLastReg();
        } else {
            condition = (lcManager.getStopsSize(nextReg) > this.controller.getQuorum() && lcManager.getNextReg() > lcManager.getLastReg());
        }
        
        // May I proceed to the synchronization phase?
        //if (lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorum2F() && lcManager.getNextReg() > lcManager.getLastReg()) {
        if (condition) {

            Logger.println("(Synchronizer.startSynchronization) installing regency " + lcManager.getNextReg());
            lcManager.setLastReg(lcManager.getNextReg()); // define last timestamp

            int regency = lcManager.getLastReg();

            // avoid memory leaks
            lcManager.removeStops(nextReg);
            lcManager.clearCurrentRequestTimedOut();
            lcManager.clearRequestsFromSTOP();

            requestsTimer.Enabled(true);
            requestsTimer.setShortTimeout(-1);
            requestsTimer.startTimer();

            //int leader = regency % this.reconfManager.getCurrentViewN(); // new leader
            int leader = lcManager.getNewLeader();
            int in = tom.getInExec(); // cid to execute
            int last = tom.getLastExec(); // last cid decided

            lm.setNewLeader(leader);

            // If I am not the leader, I have to send a STOPDATA message to the elected leader
            if (leader != this.controller.getStaticConf().getProcessId()) {

                try { // serialize content of the STOPDATA message

                    bos = new ByteArrayOutputStream();
                    out = new ObjectOutputStream(bos);

                    if (last > -1) { // content of the last decided cid

                        out.writeBoolean(true);
                        out.writeInt(last);
                        Consensus cons = execManager.getConsensus(last);
                        //byte[] decision = exec.getLearner().getDecision();

                        ////// THIS IS TO CATCH A BUG!!!!!
                        if (cons.getDecisionEpoch() == null || cons.getDecisionEpoch().propValue == null) {

                            System.out.println("[DEBUG INFO FOR LAST CID #1]");

                            if (cons.getDecisionEpoch() == null) {
                                System.out.println("No decision epoch for cid " + last);
                            } else {
                                System.out.println("epoch for cid: " + last + ": " + cons.getDecisionEpoch().toString());

                                if (cons.getDecisionEpoch().propValue == null) {
                                    System.out.println("No propose for cid " + last);
                                } else {
                                    System.out.println("Propose hash for cid " + last + ": " + Base64.encodeBase64String(tom.computeHash(cons.getDecisionEpoch().propValue)));
                                }
                            }

                            return;
                        }

                        byte[] decision = cons.getDecisionEpoch().propValue;
                        Set<ConsensusMessage> proof = cons.getDecisionEpoch().getProof();

                        out.writeObject(decision);
                        out.writeObject(proof);
                        // TODO: WILL BE NECESSARY TO ADD A PROOF!!!

                    } else {
                        out.writeBoolean(false);
                    }

                    if (in > -1) { // content of cid in execution

                        Consensus cons = execManager.getConsensus(in);

                        cons.incEts(); // make the consensus advance to the next epoch

                        int ets = cons.getEts();
                        cons.createEpoch(ets, controller);
                        Logger.println("(Synchronizer.startSynchronization) incrementing ets of consensus " + cons.getId() + " to " + ets);

                        TimestampValuePair quorumWrites;
                        if (cons.getQuorumWrites() != null) {

                            quorumWrites = cons.getQuorumWrites();

                        } else {

                            quorumWrites = new TimestampValuePair(0, new byte[0]);
                        }

                        HashSet<TimestampValuePair> writeSet = cons.getWriteSet();

                        CollectData collect = new CollectData(this.controller.getStaticConf().getProcessId(), in, quorumWrites, writeSet);

                        SignedObject signedCollect = tom.sign(collect);

                        out.writeObject(signedCollect);

                    } else {

                        Consensus cons = execManager.getConsensus(last + 1);

                        cons.incEts(); // make the consensus advance to the next epoch

                        int ets = cons.getEts();
                        cons.createEpoch(ets, controller);
                        Logger.println("(Synchronizer.startSynchronization) incrementing ets of consensus " + cons.getId() + " to " + ets);

                        CollectData collect = new CollectData(this.controller.getStaticConf().getProcessId(), last + 1, new TimestampValuePair(0, new byte[0]), new HashSet<TimestampValuePair>());

                        SignedObject signedCollect = tom.sign(collect);

                        out.writeObject(signedCollect);

                    }

                    out.flush();
                    bos.flush();

                    byte[] payload = bos.toByteArray();
                    out.close();
                    bos.close();

                    int[] b = new int[1];
                    b[0] = leader;

                    System.out.println("(Synchronizer.startSynchronization) sending STOPDATA of regency " + regency);
                    // send message SYNC to the new leader
                    communication.send(b,
                            new LCMessage(this.controller.getStaticConf().getProcessId(), TOMUtil.STOPDATA, regency, payload));

		//TODO: Turn on timeout again?
                } catch (IOException ex) {
                    ex.printStackTrace();
                    java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                } finally {
                    try {
                        out.close();
                        bos.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                // the replica might have received a SYNC that was out of context at the time it was received, but now can be processed
                Set<LCMessage> sync = getOutOfContextLC(TOMUtil.SYNC, regency);

                Logger.println("(Synchronizer.startSynchronization) Checking if there are out of context SYNC for regency " + regency);

                if (sync.size() > 0) {
                    System.out.println("(Synchronizer.startSynchronization) Processing out of context SYNC for regency " + regency);
                } else {
                    Logger.println("(Synchronizer.startSynchronization) No out of context SYNC for regency " + regency);
                }

                for (LCMessage m : sync) {
                    if (m.getSender() == lm.getCurrentLeader()) {
                        processSYNC(m.getPayload(), regency);
                        return; // makes no sense to continue, since there is only one SYNC message
                    }
                }

            } else { // If leader, I will store information that I would send in a SYNC message

                Logger.println("(Synchronizer.startSynchronization) I'm the leader for this new regency");
                LastEidData lastData = null;
                CollectData collect = null;

                if (last > -1) {  // content of the last decided cid 
                    Consensus cons = execManager.getConsensus(last);
                    //byte[] decision = exec.getLearner().getDecision();

                    ////// THIS IS TO CATCH A BUG!!!!!
                    if (cons.getDecisionEpoch() == null || cons.getDecisionEpoch().propValue == null) {

                        System.out.println("[DEBUG INFO FOR LAST CID #2]");

                        if (cons.getDecisionEpoch() == null) {
                            System.out.println("No decision epoch for cid " + last);
                        } else {
                            System.out.println("epoch for cid: " + last + ": " + cons.getDecisionEpoch().toString());
                        }
                        if (cons.getDecisionEpoch().propValue == null) {
                            System.out.println("No propose for cid " + last);
                        } else {
                            System.out.println("Propose hash for cid " + last + ": " + Base64.encodeBase64String(tom.computeHash(cons.getDecisionEpoch().propValue)));
                        }

                        return;
                    }

                    byte[] decision = cons.getDecisionEpoch().propValue;
                    Set<ConsensusMessage> proof = cons.getDecisionEpoch().getProof();

                    lastData = new LastEidData(this.controller.getStaticConf().getProcessId(), last, decision, proof);
                    // TODO: WILL BE NECESSARY TO ADD A PROOF!!!??

                } else {
                    lastData = new LastEidData(this.controller.getStaticConf().getProcessId(), last, null, null);
                }
                lcManager.addLastEid(regency, lastData);

                if (in > -1) { // content of cid being executed
                    Consensus cons = execManager.getConsensus(in);

                    cons.incEts(); // make the consensus advance to the next epoch

                    int ets = cons.getEts();
                    cons.createEpoch(ets, controller);
                    Logger.println("(Synchronizer.startSynchronization) incrementing ets of consensus " + cons.getId() + " to " + ets);

                    TimestampValuePair quorumWrites;

                    if (cons.getQuorumWrites() != null) {

                        quorumWrites = cons.getQuorumWrites();
                    } else {
                        quorumWrites = new TimestampValuePair(0, new byte[0]);
                    }

                    HashSet<TimestampValuePair> writeSet = cons.getWriteSet();

                    collect = new CollectData(this.controller.getStaticConf().getProcessId(), in, quorumWrites, writeSet);

                } else {

                    Consensus cons = execManager.getConsensus(last + 1);

                    cons.incEts(); // make the consensus advance to the next epoch

                    int ets = cons.getEts();
                    cons.createEpoch(ets, controller);
                    Logger.println("(Synchronizer.startSynchronization) incrementing ets of consensus " + cons.getId() + " to " + ets);

                    collect = new CollectData(this.controller.getStaticConf().getProcessId(), last + 1, new TimestampValuePair(0, new byte[0]), new HashSet<TimestampValuePair>());
                }

                SignedObject signedCollect = tom.sign(collect);

                lcManager.addCollect(regency, signedCollect);

                // the replica might have received STOPDATAs that were out of context at the time they were received, but now can be processed
                Set<LCMessage> stopdatas = getOutOfContextLC(TOMUtil.STOPDATA, regency);

                Logger.println("(Synchronizer.startSynchronization) Checking if there are out of context STOPDATAs for regency " + regency);
                if (stopdatas.size() > 0) {
                    System.out.println("(Synchronizer.startSynchronization) Processing " + stopdatas.size() + " out of context STOPDATAs for regency " + regency);
                } else {
                    Logger.println("(Synchronizer.startSynchronization) No out of context STOPDATAs for regency " + regency);
                }

                for (LCMessage m : stopdatas) {
                    processSTOPDATA(m, regency);
                }

            }

        }
    }

    /**
     * This method is called by the MessageHandler each time it received
     * messages related to the leader change
     *
     * @param msg Message received from the other replica
     */
    public void deliverTimeoutRequest(LCMessage msg) {

        switch (msg.getType()) {
            case TOMUtil.STOP: { // message STOP

                System.out.println("(Synchronizer.deliverTimeoutRequest) Last regency: " + lcManager.getLastReg() + ", next regency: " + lcManager.getNextReg());

                // this message is for the next leader change?
                if (msg.getReg() == lcManager.getLastReg() + 1) {

                    Logger.println("(Synchronizer.deliverTimeoutRequest) received regency change request");

                    TOMMessage[] requests = deserializeTOMMessages(msg.getPayload());

                    // store requests that came with the STOP message
                    lcManager.addRequestsFromSTOP(requests);

                    // store information about the message STOP
                    lcManager.addStop(msg.getReg(), msg.getSender());

                    processOutOfContextSTOPs(msg.getReg()); // the replica might have received STOPs
                                                            // that were out of context at the time they
                                                            // were received, but now can be processed

                    startSynchronization(msg.getReg()); // evaluate STOP messages

                } else if (msg.getReg() > lcManager.getLastReg()) { // send STOP to out of context if
                                                                    // it is for a future regency
                    System.out.println("(Synchronizer.deliverTimeoutRequest) Keeping STOP message as out of context for regency " + msg.getReg());
                    outOfContextLC.add(msg);

                } else {
                    System.out.println("(Synchronizer.deliverTimeoutRequest) Discarding STOP message");
                }
            }
            break;
            case TOMUtil.STOPDATA: { // STOPDATA messages

                int regency = msg.getReg();

                System.out.println("(Synchronizer.deliverTimeoutRequest) Last regency: " + lcManager.getLastReg() + ", next regency: " + lcManager.getNextReg());

                // Am I the new leader, and am I expecting this messages?
                if (regency == lcManager.getLastReg()
                        && this.controller.getStaticConf().getProcessId() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {

                    Logger.println("(Synchronizer.deliverTimeoutRequest) I'm the new leader and I received a STOPDATA");
                    processSTOPDATA(msg, regency);
                } else if (msg.getReg() > lcManager.getLastReg()) { // send STOPDATA to out of context if
                                                                    // it is for a future regency

                    System.out.println("(Synchronizer.deliverTimeoutRequest) Keeping STOPDATA message as out of context for regency " + msg.getReg());
                    outOfContextLC.add(msg);

                } else {
                    System.out.println("(Synchronizer.deliverTimeoutRequest) Discarding STOPDATA message");
                }
            }
            break;
            case TOMUtil.SYNC: { // message SYNC

                int regency = msg.getReg();

                System.out.println("(Synchronizer.deliverTimeoutRequest) Last regency: " + lcManager.getLastReg() + ", next regency: " + lcManager.getNextReg());

                // I am expecting this sync?
                boolean isExpectedSync = (regency == lcManager.getLastReg() && regency == lcManager.getNextReg());

                // Is this sync what I wanted to get in the previous iteration of the synchoronization phase?
                boolean islateSync = (regency == lcManager.getLastReg() && regency == (lcManager.getNextReg() - 1));

                //Did I already sent a stopdata in this iteration?
                boolean sentStopdata = (lcManager.getStopsSize(lcManager.getNextReg()) == 0); //if 0, I already purged the stops,
                                                                                              //which I only do when I am about to
                                                                                              //send the stopdata

                // I am (or was) waiting for this message, and did I received it from the new leader?
                if ((isExpectedSync || // Expected case
                        (islateSync && !sentStopdata)) && // might happen if I timeout before receiving the SYNC
                        (msg.getSender() == lm.getCurrentLeader())) {

                //if (msg.getReg() == lcManager.getLastReg() &&
                //		msg.getReg() == lcManager.getNextReg() && msg.getSender() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {
                    processSYNC(msg.getPayload(), regency);

                } else if (msg.getReg() > lcManager.getLastReg()) { // send SYNC to out of context if
                    // it is for a future regency
                    System.out.println("(Synchronizer.deliverTimeoutRequest) Keeping SYNC message as out of context for regency " + msg.getReg());
                    outOfContextLC.add(msg);

                } else {
                    System.out.println("(Synchronizer.deliverTimeoutRequest) Discarding SYNC message");
                }
            }
            break;

        }

    }

    // this method is used to verify if the leader can make the message catch-up
    // and also sends the message
    private void catch_up(int regency) {

        Logger.println("(Synchronizer.catch_up) verify STOPDATA info");
        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        LastEidData lastHighestEid = lcManager.getHighestLastEid(regency);

        int currentEid = lastHighestEid.getEid() + 1;
        HashSet<SignedObject> signedCollects = null;
        byte[] propose = null;
        int batchSize = -1;

        // normalize the collects and apply to them the predicate "sound"
        if (lcManager.sound(lcManager.selectCollects(regency, currentEid))) {

            Logger.println("(Synchronizer.catch_up) sound predicate is true");

            signedCollects = lcManager.getCollects(regency); // all original collects that the replica has received

            Decision dec = new Decision(-1); // the only purpose of this object is to obtain the batchsize,
                                                // using code inside of createPropose()

            propose = tom.createPropose(dec);
            batchSize = dec.batchSize;

            try { // serialization of the CATCH-UP message
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                out.writeObject(lastHighestEid);

		//TODO: Missing: serialization of the proof?
                out.writeInt(currentEid);
                out.writeObject(signedCollects);
                out.writeObject(propose);
                out.writeInt(batchSize);

                out.flush();
                bos.flush();

                byte[] payload = bos.toByteArray();
                out.close();
                bos.close();

                System.out.println("(Synchronizer.catch_up) sending SYNC message for regency " + regency);

                // send the CATCH-UP message
                communication.send(this.controller.getCurrentViewOtherAcceptors(),
                        new LCMessage(this.controller.getStaticConf().getProcessId(), TOMUtil.SYNC, regency, payload));

                finalise(regency, lastHighestEid, currentEid, signedCollects, propose, batchSize, true);

            } catch (IOException ex) {
                ex.printStackTrace();
                java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    out.close();
                    bos.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                    java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    //This method is invoked by the state transfer protocol to notify the replica
    // that it can end synchronization
    public void resumeLC() {

        Consensus cons = execManager.getConsensus(tempLastHighestEid.getEid());
        Epoch e = cons.getLastEpoch();

        int ets = cons.getEts();

        if (e == null || e.getTimestamp() != ets) {
            e = cons.createEpoch(ets, controller);
        } else {
            e.clear();
        }

        byte[] hash = tom.computeHash(tempLastHighestEid.getEidDecision());
        e.propValueHash = hash;
        e.propValue = tempLastHighestEid.getEidDecision();

        e.deserializedPropValue = tom.checkProposedValue(tempLastHighestEid.getEidDecision(), false);

        finalise(tempRegency, tempLastHighestEid, tempCurrentEid,
                tempSignedCollects, tempPropose, tempBatchSize, tempIAmLeader);

    }

    // this method is called on all replicas, and serves to verify and apply the
    // information sent in the catch-up message
    private void finalise(int regency, LastEidData lastHighestEid,
            int currentEid, HashSet<SignedObject> signedCollects, byte[] propose, int batchSize, boolean iAmLeader) {

        Logger.println("(Synchronizer.finalise) final stage of LC protocol");
        int me = this.controller.getStaticConf().getProcessId();
        Consensus cons = null;
        Epoch e = null;

        if (tom.getLastExec() + 1 < lastHighestEid.getEid()) { // is this a delayed replica?

            System.out.println("NEEDING TO USE STATE TRANSFER!! (" + lastHighestEid.getEid() + ")");

            tempRegency = regency;
            tempLastHighestEid = lastHighestEid;
            tempCurrentEid = currentEid;
            tempSignedCollects = signedCollects;
            tempPropose = propose;
            tempBatchSize = batchSize;
            tempIAmLeader = iAmLeader;

            execManager.getStoppedMsgs().add(acceptor.getFactory().createPropose(currentEid, 0, propose));
            stateManager.requestAppState(lastHighestEid.getEid());

            return;

        } else if (tom.getLastExec() + 1 == lastHighestEid.getEid()) { // Is this replica still executing the last decided consensus?

            //TODO: it is necessary to verify the proof
            System.out.println("I'm still at the CID before the most recent one!!! (" + lastHighestEid.getEid() + ")");

            cons = execManager.getConsensus(lastHighestEid.getEid());
            e = cons.getLastEpoch();

            int ets = cons.getEts();

            if (e == null || e.getTimestamp() != ets) {
                e = cons.createEpoch(ets, controller);
            } else {
                e.clear();
            }

            byte[] hash = tom.computeHash(lastHighestEid.getEidDecision());
            e.propValueHash = hash;
            e.propValue = lastHighestEid.getEidDecision();

            e.deserializedPropValue = tom.checkProposedValue(lastHighestEid.getEidDecision(), false);
            cons.decided(e, hash); // pass the decision to the delivery thread
        }
        byte[] tmpval = null;

        HashSet<CollectData> selectedColls = lcManager.selectCollects(signedCollects, currentEid);

        // get a value that satisfies the predicate "bind"
        tmpval = lcManager.getBindValue(selectedColls);
        Logger.println("(Synchronizer.finalise) Trying to find a binded value");

        // If such value does not exist, obtain the value written by the new leader
        if (tmpval == null && lcManager.unbound(selectedColls)) {
            Logger.println("(Synchronizer.finalise) did not found a value that might have already been decided");
            tmpval = propose;
        } else {
            Logger.println("(Synchronizer.finalise) found a value that might have been decided");
        }

        if (tmpval != null) { // did I manage to get some value?

            Logger.println("(Synchronizer.finalise) resuming normal phase");
            lcManager.removeCollects(regency); // avoid memory leaks

            cons = execManager.getConsensus(currentEid);

            cons.removeWritten(tmpval);
            cons.addWritten(tmpval);

            e = cons.getLastEpoch();

            int ets = cons.getEts();

            if (e == null || e.getTimestamp() != ets) {
                e = cons.createEpoch(ets, controller);
            } else {
                e.clear();
            }

            byte[] hash = tom.computeHash(tmpval);
            e.propValueHash = hash;
            e.propValue = tmpval;

            e.deserializedPropValue = tom.checkProposedValue(tmpval, false);

            if (cons.getDecision().firstMessageProposed == null) {
                if (e.deserializedPropValue != null
                        && e.deserializedPropValue.length > 0) {
                    cons.getDecision().firstMessageProposed = e.deserializedPropValue[0];
                } else {
                    cons.getDecision().firstMessageProposed = new TOMMessage(); // to avoid null pointer
                }
            }
            if (this.controller.getStaticConf().isBFT()) {
                e.setWrite(me, hash);
            } else {
                e.setAccept(me, hash);

                Logger.println("(Synchronizer.finalise) [CFT Mode] Setting consensus " + currentEid + " QuorumWrite tiemstamp to " + e.getConsensus().getEts() + " and value " + Arrays.toString(hash));
 	        e.getConsensus().setQuorumWrites(hash);

            }

            // resume normal operation
            execManager.restart();
            //leaderChanged = true;
            tom.setInExec(currentEid);
            if (iAmLeader) {
                Logger.println("(Synchronizer.finalise) wake up proposer thread");
                tom.imAmTheLeader();
            } // waik up the thread that propose values in normal operation

            // send a WRITE/ACCEPT message to the other replicas
            if (this.controller.getStaticConf().isBFT()) {
                Logger.println("(Synchronizer.finalise) sending WRITE message");
                communication.send(this.controller.getCurrentViewOtherAcceptors(),
                        acceptor.getFactory().createWrite(currentEid, e.getTimestamp(), e.propValueHash));
            } else {
                Logger.println("(Synchronizer.finalise) sending ACCEPT message");
                communication.send(this.controller.getCurrentViewOtherAcceptors(),
                        acceptor.getFactory().createAccept(currentEid, e.getTimestamp(), e.propValueHash));
            }
        } else {
            Logger.println("(Synchronizer.finalise) sync phase failed for regency" + regency);
        }
    }

}
