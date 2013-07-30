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
package bftsmart.tom.core;

import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignedObject;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.apache.commons.codec.binary.Base64;

import bftsmart.clientsmanagement.ClientsManager;
import bftsmart.clientsmanagement.RequestList;
import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.paxosatwar.Consensus;
import bftsmart.paxosatwar.executionmanager.Execution;
import bftsmart.paxosatwar.executionmanager.ExecutionManager;
import bftsmart.paxosatwar.executionmanager.LeaderModule;
import bftsmart.paxosatwar.executionmanager.Round;
import bftsmart.paxosatwar.executionmanager.TimestampValuePair;
import bftsmart.paxosatwar.messages.PaxosMessage;
import bftsmart.paxosatwar.roles.Acceptor;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.reconfiguration.StatusReply;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.core.timer.ForwardedMessage;
import bftsmart.tom.core.timer.RequestsTimer;
import bftsmart.tom.leaderchange.CollectData;
import bftsmart.tom.leaderchange.LCManager;
import bftsmart.tom.leaderchange.LCMessage;
import bftsmart.tom.leaderchange.LastEidData;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.BatchBuilder;
import bftsmart.tom.util.BatchReader;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;
import java.util.Set;


/**
 * This class implements a thread that uses the PaW algorithm to provide the application
 * a layer of total ordered messages
 */
public final class TOMLayer extends Thread implements RequestReceiver {

    //other components used by the TOMLayer (they are never changed)
    public ExecutionManager execManager; // Execution manager
    public LeaderModule lm; // Leader module
    public Acceptor acceptor; // Acceptor role of the PaW algorithm
    private ServerCommunicationSystem communication; // Communication system between replicas
    //private OutOfContextMessageThread ot; // Thread which manages messages that do not belong to the current execution
    private DeliveryThread dt; // Thread which delivers total ordered messages to the appication
    private StateManager stateManager = null; // object which deals with the state transfer protocol

    /** Manage timers for pending requests */
    public RequestsTimer requestsTimer;
    /** Store requests received but still not ordered */
    public ClientsManager clientsManager;
    /** The id of the consensus being executed (or -1 if there is none) */
    private int inExecution = -1;
    private int lastExecuted = -1;
    
    private MessageDigest md;
    private Signature engine;

    //the next two are used to generate non-deterministic data in a deterministic way (by the leader)
    private BatchBuilder bb = new BatchBuilder();

    /* The locks and conditions used to wait upon creating a propose */
    private ReentrantLock leaderLock = new ReentrantLock();
    private Condition iAmLeader = leaderLock.newCondition();
    private ReentrantLock messagesLock = new ReentrantLock();
    private Condition haveMessages = messagesLock.newCondition();
    private ReentrantLock proposeLock = new ReentrantLock();
    private Condition canPropose = proposeLock.newCondition();

    /** THIS IS JOAO'S CODE, RELATED TO LEADER CHANGE */
    private LCManager lcManager;
    /*************************************************************/

    private PrivateKey prk;
    public ServerViewManager reconfManager;
    
    /**
     * Creates a new instance of TOMulticastLayer
     * @param manager Execution manager
     * @param receiver Object that receives requests from clients
     * @param lm Leader module
     * @param a Acceptor role of the PaW algorithm
     * @param cs Communication system between replicas
     * @param recManager Reconfiguration Manager
     */
    public TOMLayer(ExecutionManager manager,
            ServiceReplica receiver,
            Recoverable recoverer,
            LeaderModule lm,
            Acceptor a,
            ServerCommunicationSystem cs,
            ServerViewManager recManager) {

        super("TOM Layer");
        
        this.execManager = manager;
        this.lm = lm;
        this.acceptor = a;
        this.communication = cs;
        this.reconfManager = recManager;
               
        //do not create a timer manager if the timeout is 0
        if (reconfManager.getStaticConf().getRequestTimeout() == 0){
            this.requestsTimer = null;
        }
        else this.requestsTimer = new RequestsTimer(this, communication, reconfManager); // Create requests timers manager (a thread)
       
        this.clientsManager = new ClientsManager(reconfManager, requestsTimer); // Create clients manager

        try {
            this.md = MessageDigest.getInstance("MD5"); // TODO: shouldn't it be SHA?
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        try {
            this.engine = Signature.getInstance("SHA1withRSA");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        this.prk = reconfManager.getStaticConf().getRSAPrivateKey();
        this.lcManager = new LCManager(this,recManager, md);
        this.dt = new DeliveryThread(this, receiver, recoverer, this.reconfManager); // Create delivery thread
        this.dt.start();
        this.stateManager = recoverer.getStateManager();
        stateManager.init(this, dt);
    }

    ReentrantLock hashLock = new ReentrantLock();

    /**
     * Computes an hash for a TOM message
     * @param data Data from which to generate the hash
     * @return Hash for the specified TOM message
     */
    public final byte[] computeHash(byte[] data) {
        byte[] ret = null;
        hashLock.lock();
        ret = md.digest(data);
        hashLock.unlock();

        return ret;
    }

    public SignedObject sign(Serializable obj) {
        try {
            return new SignedObject(obj, prk, engine);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    /**
     * Verifies the signature of a signed object
     * @param so Signed object to be verified
     * @param sender Replica id that supposedly signed this object
     * @return True if the signature is valid, false otherwise
     */
    public boolean verifySignature(SignedObject so, int sender) {
        try {
            return so.verify(reconfManager.getStaticConf().getRSAPublicKey(), engine);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Retrieve Communication system between replicas
     * @return Communication system between replicas
     */
    public ServerCommunicationSystem getCommunication() {
        return this.communication;
    }

    public void imAmTheLeader() {
        leaderLock.lock();
        iAmLeader.signal();
        leaderLock.unlock();
    }

    /**
     * Sets which consensus was the last to be executed
     * @param last ID of the consensus which was last to be executed
     */
    public void setLastExec(int last) {
        this.lastExecuted = last;
    }

    /**
     * Gets the ID of the consensus which was established as the last executed
     * @return ID of the consensus which was established as the last executed
     */
    public int getLastExec() {
        return this.lastExecuted;
    }

    /**
     * Sets which consensus is being executed at the moment
     *
     * @param inEx ID of the consensus being executed at the moment
     */
    public void setInExec(int inEx) {
        proposeLock.lock();
        Logger.println("(TOMLayer.setInExec) modifying inExec from " + this.inExecution + " to " + inEx);
        this.inExecution = inEx;
        if (inEx == -1  && !isRetrievingState()) {
            canPropose.signalAll();
        }
        proposeLock.unlock();
    }

    /**
     * This method blocks until the PaW algorithm is finished
     */
    public void waitForPaxosToFinish() {
        proposeLock.lock();
        canPropose.awaitUninterruptibly();
        proposeLock.unlock();
    }

    /**
     * Gets the ID of the consensus currently beign executed
     *
     * @return ID of the consensus currently beign executed (if no consensus ir executing, -1 is returned)
     */
    public int getInExec() {
        return this.inExecution;
    }

    /**
     * This method is invoked by the communication system to deliver a request.
     * It assumes that the communication system delivers the message in FIFO
     * order.
     *
     * @param msg The request being received
     */
    @Override
    public void requestReceived(TOMMessage msg) {
        // check if this request is valid and add it to the client' pending requests list
        boolean readOnly = (msg.getReqType() == TOMMessageType.UNORDERED_REQUEST);
        if (readOnly) {
            dt.deliverUnordered(msg, lcManager.getLastReg());
        } else {
        	if(msg.getReqType() == TOMMessageType.ASK_STATUS) {
        		msg.reply = null;
        		if(isRetrievingState()) {
            		msg.reply = new TOMMessage(this.reconfManager.getStaticConf().getProcessId(),
    						msg.getSession(), msg.getSequence(),
    						TOMUtil.getBytes(StatusReply.UPDATING_STATE.toString()), reconfManager.getCurrentViewId());
        		} else if(execManager.stopped()) {
            		msg.reply = new TOMMessage(this.reconfManager.getStaticConf().getProcessId(),
    						msg.getSession(), msg.getSequence(),
    						TOMUtil.getBytes(StatusReply.LEADER_CHANGE.toString()), reconfManager.getCurrentViewId());
        		} else {
            		msg.reply = new TOMMessage(this.reconfManager.getStaticConf().getProcessId(),
    						msg.getSession(), msg.getSequence(),
    						TOMUtil.getBytes(StatusReply.READY.toString()), reconfManager.getCurrentViewId(), TOMMessageType.STATUS_REPLY);
        		}
				communication.send(new int[] { msg.getSender() }, msg.reply);
        	} else {
                if (clientsManager.requestReceived(msg, true, communication)) {
                    messagesLock.lock();
                    haveMessages.signal();
                    messagesLock.unlock();
                } else {
                    Logger.println("(TOMLayer.requestReceive) the received TOMMessage " + msg + " was discarded.");
                }
        	}
        }
    }

    /**
     * Creates a value to be proposed to the acceptors. Invoked if this replica is the leader
     * @return A value to be proposed to the acceptors
     */
    private byte[] createPropose(Consensus cons) {
        // Retrieve a set of pending requests from the clients manager
        RequestList pendingRequests = clientsManager.getPendingRequests();

        int numberOfMessages = pendingRequests.size(); // number of messages retrieved
        int numberOfNonces = this.reconfManager.getStaticConf().getNumberOfNonces(); // ammount of nonces to be generated

        //for benchmarking
        if (cons.getId() > -1) { // if this is from the leader change, it doesnt matter
            cons.firstMessageProposed = pendingRequests.getFirst();
            cons.firstMessageProposed.consensusStartTime = System.nanoTime();
        }
        cons.batchSize = numberOfMessages;

        Logger.println("(TOMLayer.run) creating a PROPOSE with " + numberOfMessages + " msgs");

        return bb.makeBatch(pendingRequests, numberOfNonces, System.currentTimeMillis(),reconfManager);
    }
    /**
     * This is the main code for this thread. It basically waits until this replica becomes the leader,
     * and when so, proposes a value to the other acceptors
     */
    @Override
    public void run() {
        Logger.println("Running."); // TODO: can't this be outside of the loop?
        while (true) {
                     
            // blocks until this replica learns to be the leader for the current round of the current consensus
            leaderLock.lock();
            Logger.println("Next leader for eid=" + (getLastExec() + 1) + ": " + lm.getCurrentLeader());
            
            //******* EDUARDO BEGIN **************//
            if (/*lm.getLeader(getLastExec() + 1, 0)*/ lm.getCurrentLeader() != this.reconfManager.getStaticConf().getProcessId()) {
                iAmLeader.awaitUninterruptibly();
                //waitForPaxosToFinish();
            }
            //******* EDUARDO END **************//
            leaderLock.unlock();

            // blocks until the current consensus finishes
            proposeLock.lock();
            
            if (getInExec() != -1) { //there is some consensus running
                Logger.println("(TOMLayer.run) Waiting for consensus " + getInExec() + " termination.");
                canPropose.awaitUninterruptibly();
            }
            proposeLock.unlock();
            
            Logger.println("(TOMLayer.run) I'm the leader.");
            
            // blocks until there are requests to be processed/ordered
            messagesLock.lock();
            if (!clientsManager.havePendingRequests()) {
                haveMessages.awaitUninterruptibly();   
            }
            messagesLock.unlock();
            Logger.println("(TOMLayer.run) There are messages to be ordered.");
            

            Logger.println("(TOMLayer.run) I can try to propose.");
            
            if ((lm.getCurrentLeader() == this.reconfManager.getStaticConf().getProcessId()) && //I'm the leader
                    (clientsManager.havePendingRequests()) && //there are messages to be ordered
                    (getInExec() == -1)) { //there is no consensus in execution

                // Sets the current execution
                int execId = getLastExec() + 1;
                setInExec(execId);
                
                Consensus cons = execManager.getExecution(execId).getLearner();

                // Bypass protocol if service is not replicated
                if (reconfManager.getCurrentViewN() == 1) {
                    
                    Logger.println("(TOMLayer.run) Only one replica, bypassing consensus.");
                    
                    byte[] value = createPropose(cons);
                    
                    Execution execution = execManager.getExecution(cons.getId());
                    Round round = execution.getRound(0, reconfManager);
                    round.propValue = value;
                    round.propValueHash = computeHash(value);
                    round.getExecution().addWritten(value);
                    round.deserializedPropValue = checkProposedValue(value, true);
                    round.getExecution().getLearner().firstMessageProposed = round.deserializedPropValue[0];
                    cons.decided(round);
                    
                    //System.out.println("ESTOU AQUI!");
                    dt.delivery(cons);
                    continue;
                
                }
                execManager.getProposer().startExecution(execId,
                        createPropose(cons));
            }
        }
    }

    /**
     * Called by the current consensus's execution, to notify the TOM layer that a value was decided
     * @param cons The decided consensus
     */
    public void decided(Consensus cons) {
        this.dt.delivery(cons); // Delivers the consensus to the delivery thread
    }

    /**
     * Verify if the value being proposed for a round is valid. It verifies the
     * client signature of all batch requests.
     *
     * TODO: verify timestamps and nonces
     *
     * @param proposedValue the value being proposed
     * @return Valid messages contained in the proposed value
     */
    public TOMMessage[] checkProposedValue(byte[] proposedValue, boolean addToClientManager) {
        Logger.println("(TOMLayer.isProposedValueValid) starting");

        BatchReader batchReader = new BatchReader(proposedValue, 
                this.reconfManager.getStaticConf().getUseSignatures() == 1);

        TOMMessage[] requests = null;

        try {
            //deserialize the message
            //TODO: verify Timestamps and Nonces
            requests = batchReader.deserialiseRequests(this.reconfManager);

            if (addToClientManager) {
                for (int i = 0; i < requests.length; i++) {
                    //notifies the client manager that this request was received and get
                    //the result of its validation
                    if (!clientsManager.requestReceived(requests[i], false)) {
                        clientsManager.getClientsLock().unlock();
                        Logger.println("(TOMLayer.isProposedValueValid) finished, return=false");
                        System.out.println("failure in deserialize batch");
                        return null;
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            clientsManager.getClientsLock().unlock();
            Logger.println("(TOMLayer.isProposedValueValid) finished, return=false");
            return null;
        }
        Logger.println("(TOMLayer.isProposedValueValid) finished, return=true");

        return requests;
    }
    
    public void forwardRequestToLeader(TOMMessage request) {
        int leaderId = lm.getCurrentLeader();
        //******* EDUARDO BEGIN **************//
        if (this.reconfManager.isCurrentViewMember(leaderId)) {
            Logger.println("(TOMLayer.forwardRequestToLeader) forwarding " + request + " to " + leaderId);
            communication.send(new int[]{leaderId}, 
                new ForwardedMessage(this.reconfManager.getStaticConf().getProcessId(), request));
        }
            //******* EDUARDO END **************//
    }

    public boolean isRetrievingState() {
        //lockTimer.lock();
        boolean result =  stateManager != null && stateManager.isRetrievingState();
        //lockTimer.unlock();

        return result;
    }

    public void setNoExec() {
        Logger.println("(TOMLayer.setNoExec) modifying inExec from " + this.inExecution + " to " + -1);

        proposeLock.lock();
        this.inExecution = -1;
        //ot.addUpdate();
        canPropose.signalAll();
        proposeLock.unlock();
    }

    public void processOutOfContext() {
        for (int nextExecution = getLastExec() + 1;
                execManager.receivedOutOfContextPropose(nextExecution);
                nextExecution = getLastExec() + 1) {
            execManager.processOutOfContextPropose(execManager.getExecution(nextExecution));
        }
    }

    public StateManager getStateManager() {
        return stateManager;
    }
   
    public LCManager getLCManager() {
        return lcManager;
    }

    /*** THIS IS JOAO'S CODE, RELATED TO LEADER CHANGE */
    
    /**
     * This method is called when there is a timeout and the request has already been forwarded to the leader
     * @param requestList List of requests that the replica wanted to order but didn't manage to
     */
    public void triggerTimeout(List<TOMMessage> requestList) {

        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        requestsTimer.stopTimer();
        requestsTimer.Enabled(false);
                
        // still not in the leader change phase?
        
        if (lcManager.getNextReg() == lcManager.getLastReg()) {
            
                Logger.println("(TOMLayer.triggerTimeout) initialize synch phase");
               
                lcManager.setNextReg(lcManager.getLastReg() + 1); // define next timestamp

                int regency = lcManager.getNextReg();


                // store messages to be ordered
                lcManager.setCurrentRequestTimedOut(requestList);

                // store information about messages that I'm going to send
                lcManager.addStop(regency, this.reconfManager.getStaticConf().getProcessId());

                execManager.stop(); // stop consensus execution
            
                try { // serialize content to send in STOP message
                    out = new ObjectOutputStream(bos);

                    if (lcManager.getCurrentRequestTimedOut() != null) {

                        //TODO: If this is null, then there was no timeout. What to do?
                        byte[] msgs = bb.makeBatch(lcManager.getCurrentRequestTimedOut(), 0, 0, reconfManager);
                        out.writeBoolean(true);
                        out.writeObject(msgs);
                    } else {
                        out.writeBoolean(false);
                    }

                    byte[] payload = bos.toByteArray();

                    out.flush();
                    bos.flush();

                    out.close();
                    bos.close();
                   
                    // send STOP-message
                    Logger.println("(TOMLayer.triggerTimeout) sending STOP message to install regency " + regency);
                    communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                    new LCMessage(this.reconfManager.getStaticConf().getProcessId(), TOMUtil.STOP, regency, payload));

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

                evaluateStops(regency); // evaluate STOP messages
            
        }

    }

    // this method is called when a timeout occurs or when a STOP message is recevied
    private void evaluateStops(int nextReg) {

        boolean enterFirstPhase = this.reconfManager.getStaticConf().isBFT();
        boolean condition = false;
        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        // pass to the leader change phase if more than f messages have been received already
        if (enterFirstPhase && lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorumF() && lcManager.getNextReg() == lcManager.getLastReg()) {

           Logger.println("(TOMLayer.evaluateStops) initialize synch phase");
           requestsTimer.Enabled(false);
            requestsTimer.stopTimer();
            
            lcManager.setNextReg(lcManager.getLastReg() + 1); // define next timestamp

            int regency = lcManager.getNextReg();

            // store information about message I am going to send
            lcManager.addStop(regency, this.reconfManager.getStaticConf().getProcessId());

            execManager.stop(); // stop execution of consensus
            
            try { // serialize conent to send in the STOP message
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                if (lcManager.getCurrentRequestTimedOut() != null) {

                    //TODO: If this is null, there was no timeout. What shall be done then?
                    out.writeBoolean(true);
                    byte[] msgs = bb.makeBatch(lcManager.getCurrentRequestTimedOut(), 0, 0, reconfManager);
                    out.writeObject(msgs);
                }
                else {
                    out.writeBoolean(false);
                }

                out.flush();
                bos.flush();

                byte[] payload = bos.toByteArray();
                out.close();
                bos.close();
                
                // send message STOP
                Logger.println("(TOMLayer.evaluateStops) sending STOP message to install regency " + regency);
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                    new LCMessage(this.reconfManager.getStaticConf().getProcessId(), TOMUtil.STOP, regency, payload));

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
        
        if(this.reconfManager.getStaticConf().isBFT()) {
        	condition = lcManager.getStopsSize(nextReg) > this.reconfManager.getCertificateQuorum() && lcManager.getNextReg() > lcManager.getLastReg();
        } else {
        	condition = (lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorumStrong() && lcManager.getNextReg() > lcManager.getLastReg());
        }
        // May I proceed to the synchronization phase?
        //if (lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorum2F() && lcManager.getNextReg() > lcManager.getLastReg()) {
        if (condition) {
            
            Logger.println("(TOMLayer.evaluateStops) installing regency " + lcManager.getNextReg());
            lcManager.setLastReg(lcManager.getNextReg()); // define last timestamp

            int regency = lcManager.getLastReg();

            // avoid a memory leak
            lcManager.removeStops(nextReg);
            
            requestsTimer.Enabled(true);
            requestsTimer.setShortTimeout(-1);
            requestsTimer.setTimeout(requestsTimer.getTimer() * 2);
            requestsTimer.startTimer();

            //int leader = regency % this.reconfManager.getCurrentViewN(); // new leader
            int leader = lcManager.getNewLeader();
            int in = getInExec(); // eid to execute
            int last = getLastExec(); // last eid decided
            
            lm.setNewLeader(leader);
                    
            // If I am not the leader, I have to send a STOPDATE message to it
            if (leader != this.reconfManager.getStaticConf().getProcessId()) {

                try { // serialize content of the SYNC message
                    
                    bos = new ByteArrayOutputStream();
                    out = new ObjectOutputStream(bos);

                    if (last > -1) { // content of the last decided eid
                        
                        out.writeBoolean(true);
                        out.writeInt(last);
                        Execution exec = execManager.getExecution(last);
                        //byte[] decision = exec.getLearner().getDecision();
                        
                        ////// ISTO E PARA APANHAR UM BUG!!!!!
                        if (exec.getDecisionRound() == null || exec.getDecisionRound().propValue == null) {
                            
                            System.out.println("[DEBUG INFO FOR LAST EID #1]");
                            
                            if (exec.getDecisionRound() == null) 
                                System.out.println("No decision round for eid " + last);
                            else System.out.println("round for eid: " + last + ": " + exec.getDecisionRound().toString());
                            if (exec.getDecisionRound().propValue == null) 
                                System.out.println("No propose for eid " + last);
                            else {
                                System.out.println("Propose hash for eid " + last + ": " + Base64.encodeBase64String(computeHash(exec.getDecisionRound().propValue)));
                            }

                            return;
                        }
                        

                        byte[] decision = exec.getDecisionRound().propValue;
                        Set<PaxosMessage> proof = exec.getDecisionRound().getProof();

                        out.writeObject(decision);
                        out.writeObject(proof);
                        // TODO: WILL BE NECESSARY TO ADD A PROOF!!!

                    }

                    else out.writeBoolean(false);

                    if (in > -1) { // content of eid in execution
                        
                        Execution exec = execManager.getExecution(in);

                        TimestampValuePair quorumWeaks = exec.getQuorumWeaks();
                        HashSet<TimestampValuePair> writeSet = exec.getWriteSet();

                        CollectData collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), in, quorumWeaks, writeSet);

                        SignedObject signedCollect = sign(collect);

                        out.writeObject(signedCollect);

                    }

                    else {

                        CollectData collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), -1, new TimestampValuePair(-1, new byte[0]), new HashSet<TimestampValuePair>());

                        SignedObject signedCollect = sign(collect);

                        out.writeObject(signedCollect);

                    }

                    out.flush();
                    bos.flush();

                    byte[] payload = bos.toByteArray();
                    out.close();
                    bos.close();

                    int[] b = new int[1];
                    b[0] = leader;

                    Logger.println("(TOMLayer.evaluateStops) sending STOPDATA of regency " + regency);
                    // send message SYNC to the new leader
                    communication.send(b,
                        new LCMessage(this.reconfManager.getStaticConf().getProcessId(), TOMUtil.STOPDATA, regency, payload));

                    //TODO: Turn on timeout again
                    
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

            } else { // If leader, I will store information that I would send in a SYNC message
                
                Logger.println("(TOMLayer.evaluateStops) I'm the leader for this new regency");
                LastEidData lastData = null;
                CollectData collect = null;

                if (last > -1) {  // content of the last decided eid 
                    Execution exec = execManager.getExecution(last);
                    //byte[] decision = exec.getLearner().getDecision();

                    ////// ISTO E PARA APANHAR UM BUG!!!!!
                    if (exec.getDecisionRound() == null || exec.getDecisionRound().propValue == null) {

                        System.out.println("[DEBUG INFO FOR LAST EID #2]");

                        if (exec.getDecisionRound() == null) 
                            System.out.println("No decision round for eid " + last);
                        else System.out.println("round for eid: " + last + ": " + exec.getDecisionRound().toString());
                        if (exec.getDecisionRound().propValue == null) 
                            System.out.println("No propose for eid " + last);
                        else {
                            System.out.println("Propose hash for eid " + last + ": " + Base64.encodeBase64String(computeHash(exec.getDecisionRound().propValue)));
                        }

                        return;
                    }

                    byte[] decision = exec.getDecisionRound().propValue;
                    Set<PaxosMessage> proof = exec.getDecisionRound().getProof();
                        
                    lastData = new LastEidData(this.reconfManager.getStaticConf().getProcessId(), last, decision, proof);
                    // TODO: WILL BE NECESSARY TO ADD A PROOF!!!

                } else {
                    lastData = new LastEidData(this.reconfManager.getStaticConf().getProcessId(), last, null, null);
                }
                lcManager.addLastEid(regency, lastData);


                if (in > -1) { // content of eid being executed
                    Execution exec = execManager.getExecution(in);

                    TimestampValuePair quorumWeaks = exec.getQuorumWeaks();
                    HashSet<TimestampValuePair> writeSet = exec.getWriteSet();

                    collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), in, quorumWeaks, writeSet);

                } else {
                    collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), -1, new TimestampValuePair(-1, new byte[0]), new HashSet<TimestampValuePair>());
                }

                SignedObject signedCollect = sign(collect);

                lcManager.addCollect(regency, signedCollect);
            }

        }
    }

    /**
     * This method is called by the MessageHandler each time it received messages related
     * to the leader change
     * @param msg Message received from the other replica
     */
    public void deliverTimeoutRequest(LCMessage msg) {
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;

        switch (msg.getType()) {
            case TOMUtil.STOP: // message STOP

                {

                    // this message is for the next leader change?
                    if (msg.getReg() == lcManager.getLastReg() + 1) {

                        Logger.println("(TOMLayer.deliverTimeoutRequest) received regency change request");
                        try { // deserialize the content of the STOP message
                            
                            bis = new ByteArrayInputStream(msg.getPayload());
                            ois = new ObjectInputStream(bis);

                            boolean hasReqs = ois.readBoolean();
                            clientsManager.getClientsLock().lock();

                            if (hasReqs) {
                                // Store requests that the other replica did not manage to order
                                //TODO: The requests have to be verified!
                                byte[] temp = (byte[]) ois.readObject();
                                BatchReader batchReader = new BatchReader(temp,
                                        reconfManager.getStaticConf().getUseSignatures() == 1);
                                TOMMessage[] requests = batchReader.deserialiseRequests(reconfManager);
                            }
                            clientsManager.getClientsLock().unlock();

                            ois.close();
                            bis.close();

                        } catch (IOException ex) {
                            ex.printStackTrace();
                            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);
                        } catch (ClassNotFoundException ex) {
                            ex.printStackTrace();
                            java.util.logging.Logger.getLogger(TOMLayer.class.getName()).log(Level.SEVERE, null, ex);

                        }

                        // store information about the message STOP
                        lcManager.addStop(msg.getReg(), msg.getSender());

                        evaluateStops(msg.getReg()); // evaluate STOP messages
                    }
                }
                break;
            case TOMUtil.STOPDATA: {
            	 // STOPDATA messages
                    int regency = msg.getReg();

                    // Am I the new leader, and am I expecting this messages?
                    if (regency == lcManager.getLastReg() &&
                            this.reconfManager.getStaticConf().getProcessId() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {
                        
                        Logger.println("(TOMLayer.deliverTimeoutRequest) I'm the new leader and I received a STOPDATA");
                        //TODO: It is necessary to verify the proof of the last decided consensus and the signature of the state of the current consensus!
                        
                        LastEidData lastData = null;
                        SignedObject signedCollect = null;

                        int last = -1;
                        byte[] lastValue = null;
                        Set<PaxosMessage> proof = null;

                        try { // deserialize the content of the message
                            
                            bis = new ByteArrayInputStream(msg.getPayload());
                            ois = new ObjectInputStream(bis);

                            if (ois.readBoolean()) { // content of the last decided eid
                                
                                last = ois.readInt();

                                lastValue = (byte[]) ois.readObject();
                                proof = (Set<PaxosMessage>) ois.readObject();

                                //TODO: Proof is missing!
                            }

                            lastData = new LastEidData(msg.getSender(), last, lastValue, proof);

                            lcManager.addLastEid(regency, lastData);

                            // conteudo do eid a executar

                            signedCollect = (SignedObject) ois.readObject();

                            ois.close();
                            bis.close();

                            lcManager.addCollect(regency, signedCollect);

                            int bizantineQuorum = (reconfManager.getCurrentViewN() + reconfManager.getCurrentViewF()) / 2;
                            int cftQuorum = (reconfManager.getCurrentViewN()) / 2;
                            
                            // I already got messages from a Byzantine/Crash quorum,
                            // related to the last eid as well as for the current?
                            
                            boolean conditionBFT = (reconfManager.getStaticConf().isBFT() && lcManager.getLastEidsSize(regency) > bizantineQuorum &&
                                    lcManager.getCollectsSize(regency) > bizantineQuorum);
                            
                            boolean conditionCFT = (lcManager.getLastEidsSize(regency) > cftQuorum && lcManager.getCollectsSize(regency) > cftQuorum);

                            if (conditionBFT || conditionCFT) catch_up(regency);

                        } catch (IOException ex) {
                            ex.printStackTrace(System.err);
                        } catch (ClassNotFoundException ex) {
                            ex.printStackTrace(System.err);
                        }

                  }
            }
            break;
        case TOMUtil.SYNC: // message SYNC
            {
                int regency = msg.getReg();

                // I am waiting for this message, and I received it from the new leader?
                if (msg.getReg() == lcManager.getLastReg() &&
                        msg.getReg() == lcManager.getNextReg() && msg.getSender() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {

                    LastEidData lastHighestEid = null;
                    int currentEid = -1;
                    HashSet<SignedObject> signedCollects = null;
                    byte[] propose = null;
                    int batchSize = -1;

                    try { // deserialization of the message content
                        
                        bis = new ByteArrayInputStream(msg.getPayload());
                        ois = new ObjectInputStream(bis);

                        lastHighestEid = (LastEidData) ois.readObject();
                        currentEid = ois.readInt();
                        signedCollects = (HashSet<SignedObject>) ois.readObject();
                        propose = (byte[]) ois.readObject();
                        batchSize = ois.readInt();
                        
                        lcManager.setCollects(regency, signedCollects);

                        // the predicate "sound" is true?
                        if (lcManager.sound(lcManager.selectCollects(regency, currentEid))) {

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
            }
            break;

        }

    }

    // this method is used to verify if the leader can make the message catch-up
    // and also sends the message
    private void catch_up(int regency) {

        Logger.println("(TOMLayer.catch_up) verify STOPDATA info");
        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        LastEidData lastHighestEid = lcManager.getHighestLastEid(regency);

        int currentEid = lastHighestEid.getEid() + 1;
        HashSet<SignedObject> signedCollects = null;
        byte[] propose = null;
        int batchSize = -1;

        // normalize the collects and apply to them the predicate "sound"
        if (lcManager.sound(lcManager.selectCollects(regency, currentEid))) {
            
            Logger.println("(TOMLayer.catch_up) sound predicate is true");

            signedCollects = lcManager.getCollects(regency); // all original collects that the replica has received
            
            Consensus cons = new Consensus(-1); // the only purpose of this object is to obtain the batchsize,
                                                // using code inside of createPropose()

            propose = createPropose(cons);
            batchSize = cons.batchSize;

            try { // serialization of the CATCH-UP message
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                out.writeObject(lastHighestEid);

                //TODO: Missing: serialization of the proof
                
                out.writeInt(currentEid);
                out.writeObject(signedCollects);
                out.writeObject(propose);
                out.writeInt(batchSize);

                out.flush();
                bos.flush();

                byte[] payload = bos.toByteArray();
                out.close();
                bos.close();

                Logger.println("(TOMLayer.catch_up) sending SYNC message for regency " + regency);
                
                // send the CATCH-UP message
                communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                    new LCMessage(this.reconfManager.getStaticConf().getProcessId(), TOMUtil.SYNC, regency, payload));

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
    
    // temporary info to resume LC protocol
    private int tempRegency = -1;
    private LastEidData tempLastHighestEid = null;
    private int tempCurrentEid = -1;
    private HashSet<SignedObject> tempSignedCollects = null;
    private byte[] tempPropose = null;
    private int tempBatchSize = -1;
    private boolean tempIAmLeader = false;
                    
    public void resumeLC() {
        
        Execution exec = execManager.getExecution(tempLastHighestEid.getEid());
        Round r = exec.getLastRound();

        if (r == null) {
            r = exec.createRound(reconfManager);
        }
        else {
            r.clear();
        }
            
        byte[] hash = computeHash(tempLastHighestEid.getEidDecision());
        r.propValueHash = hash;
        r.propValue = tempLastHighestEid.getEidDecision();
            
        r.deserializedPropValue = checkProposedValue(tempLastHighestEid.getEidDecision(), false);
        
        finalise(tempRegency, tempLastHighestEid, tempCurrentEid,
                tempSignedCollects, tempPropose, tempBatchSize, tempIAmLeader);
        
    }
    
    // this method is called on all replicas, and serves to verify and apply the
    // information sent in the catch-up message
    private void finalise(int regency, LastEidData lastHighestEid,
            int currentEid, HashSet<SignedObject> signedCollects, byte[] propose, int batchSize, boolean iAmLeader) {

        Logger.println("(TOMLayer.finalise) final stage of LC protocol");
        int me = this.reconfManager.getStaticConf().getProcessId();
        Execution exec = null;
        Round r = null;

        // Is this replica late?
        if (getLastExec() + 1 < lastHighestEid.getEid()) {
            //TODO: Case in which it is necessary to apply state transfer

            System.out.println("NEEDING TO USE STATE TRANSFER!! (" + lastHighestEid.getEid() + ")");
            
            tempRegency = regency;
            tempLastHighestEid = lastHighestEid;
            tempCurrentEid = currentEid;
            tempSignedCollects = signedCollects;
            tempPropose = propose;
            tempBatchSize = batchSize;
            tempIAmLeader = iAmLeader;
            
            execManager.getStoppedMsgs().add(acceptor.getFactory().createPropose(currentEid, 0, propose, null));
            stateManager.requestAppState(lastHighestEid.getEid());
            
            return;
    
        } else if (getLastExec() + 1 == lastHighestEid.getEid()) {
        // Is this replica still executing the last decided consensus?
            
            //TODO: it is necessary to verify the proof
            
            System.out.println("I'm still at the eid before the most recent one!!! (" + lastHighestEid.getEid() + ")");
            
            exec = execManager.getExecution(lastHighestEid.getEid());
            r = exec.getLastRound();

            if (r == null) {
                r = exec.createRound(reconfManager);
            }
            else {
                r.clear();
            }
            
            byte[] hash = computeHash(lastHighestEid.getEidDecision());
            r.propValueHash = hash;
            r.propValue = lastHighestEid.getEidDecision();
            
            r.deserializedPropValue = checkProposedValue(lastHighestEid.getEidDecision(), false);
            exec.decided(r, hash); // pass the decision to the delivery thread
        }
        byte[] tmpval = null;

        HashSet<CollectData> selectedColls = lcManager.selectCollects(signedCollects, currentEid);

        // get a value that satisfies the predicate "bind"
        tmpval = lcManager.getBindValue(selectedColls);

        // If such value does not exist, obtain the value written by the new leader
        if (tmpval == null && lcManager.unbound(selectedColls)) {
            Logger.println("(TOMLayer.finalise) did not found a value that might have already been decided");
            tmpval = propose;
        }
        else Logger.println("(TOMLayer.finalise) found a value that might have been decided");

        if (tmpval != null) { // did I manage to get some value?
            
            Logger.println("(TOMLayer.finalise) resuming normal phase");
            lcManager.removeCollects(regency); // avoid memory leaks
            
            exec = execManager.getExecution(currentEid);
            exec.incEts();

            exec.removeWritten(tmpval);
            exec.addWritten(tmpval);

            r = exec.getLastRound();
            
            if (r == null) {
                r = exec.createRound(reconfManager);
            }
            else {
                r.clear();
            }

            byte[] hash = computeHash(tmpval);
            r.propValueHash = hash;
            r.propValue = tmpval;
            
            r.deserializedPropValue = checkProposedValue(tmpval, false);

            if(exec.getLearner().firstMessageProposed == null) {
                   if (r.deserializedPropValue != null &&
                            r.deserializedPropValue.length > 0)
                        exec.getLearner().firstMessageProposed = r.deserializedPropValue[0];
                   else exec.getLearner().firstMessageProposed = new TOMMessage(); // to avoid null pointer
            }
            r.setWeak(me, hash);

            // resume normal operation
            execManager.restart();
            //leaderChanged = true;
            setInExec(currentEid);
            if (iAmLeader) {
                Logger.println("(TOMLayer.finalise) wake up proposer thread");
                imAmTheLeader();
            } // waik up the thread that propose values in normal operation
            Logger.println("(TOMLayer.finalise) sending WEAK message");
            
            //System.out.println(regency + " // WEAK (R: " + r.getNumber() + "): " + Base64.encodeBase64String(r.propValueHash));
            // send a WEAK message to the other replicas
            communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                    acceptor.getFactory().createWeak(currentEid, r.getNumber(), r.propValueHash));

        }

        else Logger.println("(TOMLayer.finalise) sync phase failed for regency" + regency);
    }
    /**************************************************************/
}
