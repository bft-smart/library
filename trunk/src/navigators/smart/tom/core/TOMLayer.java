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

package navigators.smart.tom.core;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SignedObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import java.util.logging.Level;
import navigators.smart.clientsmanagement.ClientsManager;
import navigators.smart.clientsmanagement.PendingRequests;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.paxosatwar.Consensus;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.statemanagment.StateLog;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.timer.RTInfo;
import navigators.smart.tom.core.timer.RequestsTimer;
import navigators.smart.tom.core.timer.messages.ForwardedMessage;
import navigators.smart.tom.core.timer.messages.RTCollect;
import navigators.smart.tom.core.timer.messages.RTLeaderChange;
import navigators.smart.tom.core.timer.messages.RTMessage;
import navigators.smart.tom.util.BatchBuilder;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.Storage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;


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
    private OutOfContextMessageThread ot; // Thread which manages messages that do not belong to the current execution
    private DeliveryThread dt; // Thread which delivers total ordered messages to the appication
    private TOMConfiguration conf; // TOM configuration
    /** Manage timers for pending requests */
    public RequestsTimer requestsTimer;
    /** Store requests received but still not ordered */
    public ClientsManager clientsManager;
    /** The id of the consensus being executed (or -1 if there is none) */
    private int inExecution = -1;
    private int lastExecuted = -1;
    private Map<Integer, RTInfo> timeoutInfo = new HashMap<Integer, RTInfo>();
    private ReentrantLock lockTI = new ReentrantLock();
    private TOMRequestReceiver receiver;
    //the next two are used to generate message digests
    private MessageDigest md;

    //the next two are used to generate non-deterministic data in a deterministic way (by the leader)
    private Random random = new Random();
    private long lastTimestamp = 0;

    /* The locks and conditions used to wait upon creating a propose */
    private ReentrantLock leaderLock = new ReentrantLock();
    private Condition iAmLeader = leaderLock.newCondition();
    private ReentrantLock messagesLock = new ReentrantLock();
    private Condition haveMessages = messagesLock.newCondition();
    private ReentrantLock proposeLock = new ReentrantLock();
    private Condition canPropose = proposeLock.newCondition();

    /* flag that indicates that the lader changed between the last propose and
    this propose. This flag is changed on updateLeader (to true) and decided
    (to false) and used in run.*/
    private boolean leaderChanged = true;

    /* The next fields are used only for benchmarking */
    private static final int BENCHMARK_PERIOD = 100;
    private long numMsgsReceived;
    private Storage stConsensusDuration;
    private Storage stConsensusBatch;

    /**
     * Creates a new instance of TOMulticastLayer
     * @param manager Execution manager
     * @param receiver Object that receives requests from clients
     * @param lm Leader module
     * @param a Acceptor role of the PaW algorithm
     * @param cs Communication system between replicas
     * @param conf TOM configuration
     */
    public TOMLayer(ExecutionManager manager,
            TOMRequestReceiver receiver,
            LeaderModule lm,
            Acceptor a,
            ServerCommunicationSystem cs,
            TOMConfiguration conf) {

        super("TOM Layer");

        this.execManager = manager;
        this.receiver = receiver;
        this.lm = lm;
        this.acceptor = a;
        this.communication = cs;
        this.conf = conf;
        this.numMsgsReceived = 0;
        this.stConsensusBatch = new Storage(BENCHMARK_PERIOD);
        this.stConsensusDuration = new Storage(BENCHMARK_PERIOD);

        //do not create a timer manager if the timeout is 0
        if (conf.getRequestTimeout()==0){
            this.requestsTimer = null;
        }
        else this.requestsTimer = new RequestsTimer(this, conf.getRequestTimeout()); // Create requests timers manager (a thread)

        this.clientsManager = new ClientsManager(conf, requestsTimer); // Create clients manager

        try {
            this.md = MessageDigest.getInstance("MD5"); // TODO: nao devia ser antes SHA?
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        this.ot = new OutOfContextMessageThread(this); // Create out of context thread
        this.ot.start();

        this.dt = new DeliveryThread(this, receiver, conf); // Create delivery thread
        this.dt.start();

        /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS E TRANSFERENCIA DE ESTADO*/
        stateManager = new StateManager(this.conf.getCheckpoint_period(), this.conf.getF());
        /*******************************************************/
    }

    /**
     * Retrieve TOM configuration
     * @return TOM configuration
     */
    public final TOMConfiguration getConf() {
        return this.conf;
    }

    /**
     * Computes an hash for a TOM message
     * @param message
     * @return Hash for teh specified TOM message
     */
    public final byte[] computeHash(byte[] data) {
        return md.digest(data);
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
    public void setLastExec(int last) { // TODO:  Condiçao de corrida?
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
        Logger.println("(TOMLayer.setInExec) modifying inExec from " + this.inExecution + " to " + inEx);

        proposeLock.lock();
        this.inExecution = inEx;
        if (inEx == -1
        /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            && !isRetrievingState()
        /******************************************************************/
        ) {
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
     * This method is invoked by the comunication system to deliver a request.
     * It assumes that the communication system delivers the message in FIFO
     * order.
     *
     * @param msg The request being received
     */
    public void requestReceived(TOMMessage msg) {
        //numMsgsReceived++;

        /**********************************************************/
        /********************MALICIOUS CODE************************/
        /**********************************************************/
        //first server always ignores messages from the first client (with n=4)
        /*
        if (conf.getProcessId() == 0 && msg.getSender() == 4) {
        return;
        }
         */
        /**********************************************************/
        /**********************************************************/
        /**********************************************************/
        if (clientsManager.requestReceived(msg, true, !msg.isReadOnlyRequest())) { // check if this request is valid
            if (msg.isReadOnlyRequest()) {
                receiver.receiveMessage(msg);
            } else {
                messagesLock.lock();
                haveMessages.signal();
                messagesLock.unlock();

                /*
                //Logger.println("(TOMLayer.requestReceive) (" + msg.getSender() + "," + msg.getSequence() + "," + TOMUtil.byteArrayToString(msg.getContent()) + ") received");
                if (numMsgsReceived % 1000 == 0) {
                    Logger.println("Total number of messages received from clients:" + numMsgsReceived);
                }
                */
            }
        } else {
            Logger.println("(TOMLayer.requestReceive) the received TOMMessage " + msg + " was discarded.");
        }

    }

    /**
     * Creates a value to be proposed to the acceptors. Invoked if this replica is the leader
     * @return A value to be proposed to the acceptors
     */
    private byte[] createPropose(Consensus cons) {
        // Retrieve a set of pending requests from the clients manager
        PendingRequests pendingRequests = clientsManager.getPendingRequests();

        int numberOfMessages = pendingRequests.size(); // number of messages retrieved
        int numberOfNonces = conf.getNumberOfNonces(); // ammount of nonces to be generated

        cons.batchSize = numberOfMessages;
        /*
        // These instructions are used only for benchmarking
        stConsensusBatch.store(numberOfMessages);
        if (stConsensusBatch.getCount() % BENCHMARK_PERIOD == 0) {
            System.out.println("#Media de batch dos ultimos " + BENCHMARK_PERIOD + " consensos: " + stConsensusBatch.getAverage(true));
            stConsensusBatch.reset();
        }
        */
        Logger.println("(TOMLayer.run) creating a PROPOSE with " + numberOfMessages + " msgs");

        int totalMessageSize = 0; //total size of the messages being batched
        byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
        byte[][] signatures = new byte[numberOfMessages][]; //bytes of the message (or its hash)

        // Fill the array of bytes for the messages/signatures being batched
        int i = 0;
        for (Iterator<TOMMessage> li = pendingRequests.iterator(); li.hasNext(); i++) {
            TOMMessage msg = li.next();
            //Logger.println("(TOMLayer.run) adding req " + msg + " to PROPOSE");
            messages[i] = msg.serializedMessage;
            signatures[i] = msg.serializedMessageSignature;

            totalMessageSize += messages[i].length;
        }

        // Create a batch of messages
        BatchBuilder bb = new BatchBuilder(numberOfMessages, numberOfNonces, totalMessageSize, conf.getUseSignatures()==1);
        for (i = 0; i < numberOfMessages; i++) {
            bb.putMessage(messages[i], false, signatures[i]);
        }

        //create a timestamp and a number of nonces to be together with the
        //request to deal with nondeterminism
        bb.putTimestamp(System.currentTimeMillis());
        if (numberOfNonces > 0) {
            byte[] nonces = new byte[numberOfNonces];
            random.nextBytes(nonces);
            bb.putNonces(nonces);
        }

        return bb.getByteArray(); // return the batch
    }

    /**
     * This is the main code for this thread. It basically waits until this replica becomes the leader,
     * and when so, proposes a value to the other acceptors
     */
    @Override
    public void run() {
        /*
        Storage st = new Storage(BENCHMARK_PERIOD/2);
        long start=-1;
        int counter =0;
         */
        while (true) {
            Logger.println("(TOMLayer.run) Running."); // TODO: isto n podia passar para fora do ciclo?

            // blocks until this replica learns to be the leader for the current round of the current consensus
            leaderLock.lock();
            Logger.println("(TOMLayer.run) Next leader for eid=" + (getLastExec() + 1) + ": " + lm.getLeader(getLastExec() + 1, 0));
            if (lm.getLeader(getLastExec() + 1, 0) != conf.getProcessId()) {
                iAmLeader.awaitUninterruptibly();
            }
            leaderLock.unlock();
            Logger.println("(TOMLayer.run) I'm the leader.");

            // blocks until there are requests to be processed/ordered
            messagesLock.lock();
            if (!clientsManager.havePendingRequests()) {
                haveMessages.awaitUninterruptibly();
            }
            messagesLock.unlock();
            Logger.println("(TOMLayer.run) There are messages to be ordered.");

            // blocks until the current consensus finishes
            proposeLock.lock();
            if (getInExec() != -1 && !leaderChanged) { //there is some consensus running and the leader not changed
                Logger.println("(TOMLayer.run) Waiting that consensus " + getInExec() + " terminates.");
                canPropose.awaitUninterruptibly();
            }
            proposeLock.unlock();

            Logger.println("(TOMLayer.run) I can try to propose.");
            if ((lm.getLeader(getLastExec() + 1, 0) == conf.getProcessId()) && //I'm the leader
                    (clientsManager.havePendingRequests()) && //there are messages to be ordered
                    (getInExec() == -1 || leaderChanged)) { //there is no consensus in execution

                leaderChanged = false;

                // Sets the current execution
                int execId = getLastExec() + 1;
                setInExec(execId);

                //Logger.println("(TOMLayer.run) Waiting for acceptor semaphore to be released.");
                //acceptor.getMEZone();
                Execution exec = execManager.getExecution(execId);
                //acceptor.leaveMEZone();
                //Logger.println("(TOMLayer.run) Acceptor semaphore acquired");

                exec.getLearner().propose(createPropose(exec.getLearner()));

            /*
            if (counter>=BENCHMARK_PERIOD/2)
            st.store(System.nanoTime()-start);

            counter++;
             */
            } else {
                /*
                System.out.println("I should be the leader, there should be messages to order and no consensus running:");
                System.out.println(">>leader: " + lm.getLeader(getLastExec()+1,0));
                System.out.println(">>consenso em exec?: " + getInExec());
                 */
            }
        /*
        if (st.getCount()==BENCHMARK_PERIOD/2){
        System.out.println("---------------------------------------------");
        System.out.println("CREATE_PROPOSE total delay: Average time for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getAverage(true)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Standard desviation for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getDP(true)/1000 + " us ");
        System.out.println("CREATE_PROPOSE total delay: Average time for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getAverage(false)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Standard desviation for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getDP(false)/1000 + " us ");
        System.out.println("CREATE_PROPOSE total delay: Maximum time for "+BENCHMARK_PERIOD/2+" executions (-10%) = "+st.getMax(true)/1000+ " us ");
        System.out.println("CREATE_PROPOSE total delay: Maximum time for "+BENCHMARK_PERIOD/2+" executions (all samples) = "+st.getMax(false)/1000+ " us ");
        System.out.println("---------------------------------------------");

        st = new Storage(BENCHMARK_PERIOD/2);
        counter=0;
        }
         */
        }
    }

    //called by the DeliveryThread to inform that msg was delivered to the app
/*    public void cleanTimers(TOMMessage msg) {
    timer.cancelTimer(msg.getId());
    removeTimeoutInfo(msg.getId());
    }
     */
    /**
     * Called by the current consensus's execution, to notify the TOM layer that a value was decided
     * @param cons The decided consensus
     */
    public void decided(Consensus cons) {
        // These instructions are used for benchmarking
        /*
        stConsensusDuration.store(System.currentTimeMillis() - cons.startTime);
        if (stConsensusDuration.getCount() == BENCHMARK_PERIOD) {
            System.out.println("#Media dos ultimos " + BENCHMARK_PERIOD + " consensos: " + stConsensusDuration.getAverage(true) + " ms");
            stConsensusDuration.reset();
        }
        */
        cons.executionTime = System.currentTimeMillis() - cons.startTime;
        this.dt.delivery(cons); // Delivers the consensus to the delivery thread
    }

    /**
     * Verify if the value being proposed for a round is valid. It verifies the
     * client signature of all batch requests.
     *
     * TODO: verify timestamps and nonces
     *
     * @param round the Round for which this value is being proposed
     * @param proposedValue the value being proposed
     * @return
     */
    public boolean isProposedValueValid(Round round, byte[] proposedValue) {
        Logger.println("(TOMLayer.isProposedValueValid) starting");
        BatchReader batchReader = new BatchReader(proposedValue, conf.getUseSignatures()==1);

        int numberOfMessages = batchReader.getNumberOfMessages();

        TOMMessage[] requests = new TOMMessage[numberOfMessages];

        //Logger.println("(TOMLayer.isProposedValueValid) Waiting for clientsManager lock");
        //clientsManager.getClientsLock().lock();
        //Logger.println("(TOMLayer.isProposedValueValid) Got clientsManager lock");
        for (int i = 0; i < numberOfMessages; i++) {
            //read the message and its signature from the batch
            int messageSize = batchReader.getNextMessageSize();

            byte[] message = new byte[messageSize];
            batchReader.getNextMessage(message);

            byte[] signature = null;
            if (conf.getUseSignatures()==1){
                signature = new byte[TOMUtil.getSignatureSize()];
                batchReader.getNextSignature(signature);
            }

            //deserialize the message
            try {
                DataInputStream ois = new DataInputStream(new ByteArrayInputStream(message));
                TOMMessage tm = new TOMMessage();
                tm.readExternal(ois);
                requests[i] = tm;
            //requests[i] = (TOMMessage) ois.readObject();
            } catch (Exception e) {
                e.printStackTrace();
                clientsManager.getClientsLock().unlock();
                Logger.println("(TOMLayer.isProposedValueValid) finished, return=false");
                return false;
            }
            requests[i].serializedMessage = message;
            requests[i].serializedMessageSignature = signature;

            //notifies the client manager that this request was received and get
            //the result of its validation
            if (!clientsManager.requestReceived(requests[i], false)) {
                clientsManager.getClientsLock().unlock();
                Logger.println("(TOMLayer.isProposedValueValid) finished, return=false");
                return false;
            }
        }
        //clientsManager.getClientsLock().unlock();
        Logger.println("(TOMLayer.isProposedValueValid) finished, return=true");
        round.deserializedPropValue = requests;

        //TODO: verify Timestamps and Nonces
        return true;
    }

    /**
     * TODO: este metodo nao e usado. Pode desaparecer?
     * @param br
     * @return
     */
    public final boolean verifyTimestampAndNonces(BatchReader br) {
        long timestamp = br.getTimestamp();

        if (conf.canVerifyTimestamps()) {
            //br.ufsc.das.util.tom.Logger.println("(TOMLayer.verifyTimestampAndNonces) verifying timestamp "+timestamp+">"+lastTimestamp+"?");
            if (timestamp > lastTimestamp) {
                lastTimestamp = timestamp;
            } else {
                System.err.println("########################################################");
                System.err.println("- timestamp received " + timestamp + " <= " + lastTimestamp);
                System.err.println("- maybe the proposer have a non-synchronized clock");
                System.err.println("########################################################");
                return false;
            }
        }

        return br.getNumberOfNonces() == conf.getNumberOfNonces();
    }

    /**
     * Invoked when a timeout for a TOM message is triggered.
     *
     * @param reqId Request ID of the message to which the timeout is related to
     * @return True if the request is still pending and the timeout was not triggered before, false otherwise
     */
    public boolean requestTimeout(List<TOMMessage> requestList) {
        List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();

        //verify if the request is still pending
        for (Iterator<TOMMessage> i = requestList.listIterator(); i.hasNext();) {
            TOMMessage request = i.next();
            if (clientsManager.isPending(request.getId())) {
                RTInfo rti = getTimeoutInfo(request.getId());
                if (!rti.isTimeout(conf.getProcessId())) {
                    serializedRequestList.add(
                            new byte[][]{request.serializedMessage, request.serializedMessageSignature});
                    timeout(conf.getProcessId(), request, rti);
                    Logger.println("(TOMLayer.requestTimeout) Must send timeout for reqId=" + request.getId());
                }
            }
        }

        if (!requestList.isEmpty()) {
            sendTimeoutMessage(serializedRequestList);
            return true;
        } else {
            return false;
        }
    }

    public void forwardRequestToLeader(TOMMessage request) {
        int leaderId = lm.getLeader(getLastExec() + 1, 0);
        Logger.println("(TOMLayer.forwardRequestToLeader) forwarding " + request + " to " + leaderId);
        communication.send(new int[]{leaderId}, new ForwardedMessage(conf.getProcessId(), request));
    }

    /**
     * Sends a RT-TIMEOUT message to other processes.
     *
     * @param request the message that caused the timeout
     */
    public void sendTimeoutMessage(List<byte[][]> serializedRequestList) {
        communication.send(execManager.getOtherAcceptors(),
                new RTMessage(TOMUtil.RT_TIMEOUT, -1, conf.getProcessId(), serializedRequestList));
    }

    /**
     * Sends a RT-COLLECT message to other processes
     * TODO: Se se o novo leader for este processo, nao e enviada nenhuma mensagem. Isto estara bem feito?
     * @param reqId ID of the message which triggered the timeout
     * @param collect Proof for the timeout
     */
    public void sendCollectMessage(int reqId, RTCollect collect) {
        RTMessage rtm = new RTMessage(TOMUtil.RT_COLLECT, reqId,
                conf.getProcessId(), acceptor.sign(collect));

        if (collect.getNewLeader() == conf.getProcessId()) {
            RTInfo rti = getTimeoutInfo(reqId);
            collect((SignedObject) rtm.getContent(), conf.getProcessId(), rti);
        } else {
            int[] target = {collect.getNewLeader()};
            this.communication.send(target, rtm);
        }

    }

    /**
     * Sends a RT-LEADER message to other processes. It also updates the leader
     *
     * @param reqId ID of the message which triggered the timeout
     * @param timeout Timeout number
     * @param rtLC Proofs for the leader change
     */
    public void sendNewLeaderMessage(int reqId, RTLeaderChange rtLC) {
        RTMessage rtm = new RTMessage(TOMUtil.RT_LEADER, reqId, conf.getProcessId(), rtLC);
        //br.ufsc.das.util.Logger.println("Atualizando leader para "+rtLC.newLeader+" a partir de "+rtLC.start);
        updateLeader(reqId, rtLC.start, rtLC.newLeader);

        communication.send(execManager.getOtherAcceptors(), rtm);
    }

    /**
     * Updates the leader of the PaW algorithm. This is triggered upon a timeout
     * for a pending message.
     *
     * @param reqId ID of the message which triggered the timeout
     * @param start Consensus where the new leader belongs
     * @param newLeader Replica ID of the new leader
     * @param timeout Timeout number
     */
    private void updateLeader(int reqId, int start, int newLeader) {
        lm.addLeaderInfo(start, 0, newLeader); // update the leader
        leaderChanged = true;

        leaderLock.lock(); // Signal the TOMlayer thread, if this replica is the leader
        if (lm.getLeader(getLastExec() + 1, 0) == conf.getProcessId()) {
            iAmLeader.signal();
        }
        leaderLock.unlock();

        removeTimeoutInfo(reqId); // remove timeout infos
        //requestsTimer.startTimer(clientsManager.getPending(reqId)); // restarts the timer
        execManager.restart(); // restarts the execution manager
    }

    /**
     * This method is invoked when the comunication system needs to deliver a message related to timeouts
     * for a pending TOM message
     * @param msg The timeout related message being delivered
     */
    public void deliverTimeoutRequest(RTMessage msg) {
        switch (msg.getRTType()) {
            case TOMUtil.RT_TIMEOUT:
                 {
                    Logger.println("(TOMLayer.deliverTimeoutRequest) receiving timeout message from " + msg.getSender());
                    List<byte[][]> serializedRequestList = (List<byte[][]>) msg.getContent();

                    for (Iterator<byte[][]> i = serializedRequestList.iterator(); i.hasNext();) {
                        byte[][] serializedRequest = i.next();

                        if (serializedRequest == null || serializedRequest.length != 2) {
                            return;
                        }

                        TOMMessage request;

                        //deserialize the message
                        try {
                            DataInputStream ois = new DataInputStream(
                                    new ByteArrayInputStream(serializedRequest[0]));
                            request = new TOMMessage();
                            request.readExternal(ois);
                        } catch (Exception e) {
                            e.printStackTrace();
                            clientsManager.getClientsLock().unlock();
                            Logger.println("(TOMLayer.deliverTimeoutRequest) invalid request.");
                            return;
                        }

                        request.serializedMessage = serializedRequest[0];
                        request.serializedMessageSignature = serializedRequest[1];

                        if (clientsManager.requestReceived(request, false)) { //Is this a pending message?
                            RTInfo rti = getTimeoutInfo(request.getId());
                            timeout(msg.getSender(), request, rti);
                        }
                    }
                }
                break;
            case TOMUtil.RT_COLLECT:
                 {
                    Logger.println("(TOMLayer.deliverTimeoutRequest) receiving collect for message " + msg.getReqId() + " from " + msg.getSender());
                    SignedObject so = (SignedObject) msg.getContent();
                    if (acceptor.verifySignature(so, msg.getSender())) { // valid signature?
                        try {
                            RTCollect rtc = (RTCollect) so.getObject();
                            int reqId = rtc.getReqId();

                            int nl = chooseNewLeader();

                            if (nl == conf.getProcessId() && nl == rtc.getNewLeader()) { // If this is process the new leader?
                                RTInfo rti = getTimeoutInfo(reqId);
                                collect(so, msg.getSender(), rti);
                            }
                        } catch (ClassNotFoundException cnfe) {
                            cnfe.printStackTrace(System.err);
                        } catch (IOException ioe) {
                            ioe.printStackTrace(System.err);
                        }
                    }
                }
                break;
            case TOMUtil.RT_LEADER:
                 {
                    Logger.println("1 recebendo newLeader de " + msg.getSender());
                    RTLeaderChange rtLC = (RTLeaderChange) msg.getContent();
                    RTCollect[] rtc = getValid(msg.getReqId(), rtLC.proof);

                    if (rtLC.isAGoodStartLeader(rtc, conf.getF())) { // Is it a legitm and valid leader?
                        Logger.println("Atualizando leader para " + rtLC.newLeader + " a partir de " + rtLC.start);
                        updateLeader(msg.getReqId(), rtLC.start, rtLC.newLeader);
                    //FALTA... eliminar dados referentes a consensos maiores q start.
                    }
                }
                break;
        }
    }

    /**
     * Retrieves the timeout information for a given timeout. If the timeout
     * info does not exist, we create one.
     *
     * @param reqId ID of the message which triggered the timeout
     * @return The timeout information
     */
    public RTInfo getTimeoutInfo(int reqId) {
        lockTI.lock();
        RTInfo ti = timeoutInfo.get(reqId);
        if (ti == null) {
            ti = new RTInfo(this.conf, reqId, this);
            timeoutInfo.put(reqId, ti);
        }
        lockTI.unlock();
        return ti;
    }

    /**
     * Removes the timeout information for a given timeout.
     *
     * @param reqId ID of the message which triggered the timeout
     * @return The timeout information
     */
    private void removeTimeoutInfo(int reqId) {
        lockTI.lock();
        timeoutInfo.remove(reqId);
        lockTI.unlock();
    }

    /**
     * Invoked by the TOM layer to notify that a  timeout ocurred in a replica, and to
     * compute the necessary tasks
     * @param a Replica ID where this timeout occurred
     * @param request the request that provoked the timeout
     * @param rti the timeout info for this request
     */
    public void timeout(int acceptor, TOMMessage request, RTInfo rti) {
        rti.setTimeout(acceptor);

        int reqId = rti.getRequestId();

        if (rti.countTimeouts() > execManager.quorumF && !rti.isTimeout(conf.getProcessId())) {
            rti.setTimeout(conf.getProcessId());

            List<byte[][]> serializedRequestList = new LinkedList<byte[][]>();
            serializedRequestList.add(
                    new byte[][]{request.serializedMessage, request.serializedMessageSignature});

            sendTimeoutMessage(serializedRequestList);
        /*
        if (requestsTimer != null) {
        requestsTimer.startTimer(clientsManager.getPending(reqId));
        }
         */
        }

        if (rti.countTimeouts() > execManager.quorumStrong && !rti.isCollected()) {
            rti.setCollected();
            /*
            requestsTimer.stopTimer(clientsManager.getPending(reqId));
             */
            execManager.stop();

            int newLeader = chooseNewLeader();

            int last = -1;
            if (getInExec() != -1) {
                last = getInExec();
            } else {
                last = getLastExec();
            }

            Logger.println("(TOMLayer.timeout) sending COLLECT to " + newLeader +
                    " for " + reqId + " with last execution = " + last);
            sendCollectMessage(reqId, new RTCollect(newLeader, last, reqId));
        }
    }

    /**
     * Invoked by the TOM layer when a collect message is received, and to
     * compute the necessary tasks
     * @param c Proof from the replica that sent the message
     * @param a ID of the replica which sent the message
     */
    public void collect(SignedObject c, int a, RTInfo rti) {
        Logger.println("COLLECT 1");
        rti.setCollect(a, c);

        if (rti.countCollect() > 2 * conf.getF() && !rti.isNewLeaderSent()) {
            rti.setNewLeaderSent();
            Logger.println("COLLECT 2");

            SignedObject collect[] = rti.getCollect();

            RTCollect[] rtc = new RTCollect[collect.length];
            for (int i = 0; i < collect.length; i++) {
                if (collect[i] != null) {
                    try {
                        rtc[i] = (RTCollect) collect[i].getObject();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            Logger.println("COLLECT 3");
            RTInfo.NextLeaderAndConsensusInfo nextLeaderCons =
                    rti.getStartLeader(rtc, conf.getF());
            RTLeaderChange rtLC = new RTLeaderChange(collect, nextLeaderCons.leader,
                    nextLeaderCons.cons);

            sendNewLeaderMessage(rti.getRequestId(), rtLC);
        }
    }

    private int chooseNewLeader() {
        int lastRoundNumber = 0; //the number of the last round successfully executed

        Execution lastExec = execManager.getExecution(getLastExec());
        if (lastExec != null) {
            Round lastRound = lastExec.getDecisionRound();
            if (lastRound != null) {
                lastRoundNumber = lastRound.getNumber();
            }
        }

        return (lm.getLeader(getLastExec(), lastRoundNumber) + 1) % conf.getN();
    }

    /**
     * Gets an array of valid RTCollect proofs
     *
     * @param reqId ID of the message which triggered the timeout
     * @param timeout Timeout number
     * @param proof Array of signed objects containing the proofs to be verified
     * @return The sub-set of proofs that are valid
     */
    private RTCollect[] getValid(int reqId, SignedObject[] proof) {
        Collection<RTCollect> valid = new HashSet<RTCollect>();
        try {
            for (int i = 0; i < proof.length; i++) {
                if (proof[i] != null && acceptor.verifySignature(proof[i], i)) { // is the signature valid?
                    RTCollect rtc = (RTCollect) proof[i].getObject();
                    // Does this proof refers to the specified message id and timeout?
                    if (rtc != null && rtc.getReqId() == reqId) {
                        valid.add(rtc);
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        return valid.toArray(new RTCollect[0]); // return the valid proofs ans an array
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    private StateManager stateManager = null;
    private ReentrantLock lockState = new ReentrantLock();

    public void saveState(byte[] state, int lastEid, int decisionRound, int leader) {

        StateLog log = stateManager.getLog();

        lockState.lock();

        log.newCheckpoint(state);
        log.setLastEid(-1);
        log.setLastCheckpointEid(lastEid);
        log.setLastCheckpointRound(decisionRound);
        log.setLastCheckpointLeader(leader);

        /************************* TESTE *************************
        System.out.println("[TOMLayer.saveState]");
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (log.getState()[i] & 0x000000FF) << shift;
        }
        System.out.println("//////////////////CHECKPOINT//////////////////////");
        System.out.println("Estado: " + value);
        System.out.println("Checkpoint: " + log.getLastCheckpointEid());
        System.out.println("Ultimo EID: " + log.getLastEid());
        System.out.println("//////////////////////////////////////////////////");
        System.out.println("[/TOMLayer.saveState]");
        /************************* TESTE *************************/
        
        lockState.unlock();
    }
    public void saveBatch(byte[] batch, int lastEid, int decisionRound, int leader) {

        StateLog log = stateManager.getLog();

        lockState.lock();
        
        log.addMessageBatch(batch, decisionRound, leader);
        log.setLastEid(lastEid);

        /************************* TESTE *************************
        System.out.println("[TOMLayer.saveBatch]");
        byte[][] batches = log.getMessageBatches();
        int count = 0;
        for (int i = 0; i < batches.length; i++)
            if (batches[i] != null) count++;

        System.out.println("//////////////////////BATCH///////////////////////");
        //System.out.println("Total batches (according to StateManager): " + stateManager.getLog().getNumBatches());
        System.out.println("Total batches (actually counted by this code): " + count);
        System.out.println("Ultimo EID: " + log.getLastEid());
        //System.out.println("Espaco restante para armazenar batches: " + (stateManager.getLog().getMessageBatches().length - count));
        System.out.println("//////////////////////////////////////////////////");
        System.out.println("[/TOMLayer.saveBatch]");
        /************************* TESTE *************************/

        lockState.unlock();
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    
    public void requestState(int me, int[] otherAcceptors, int sender, int eid) {

        /************************* TESTE *************************/
        System.out.println("[TOMLayer.requestState]");
        System.out.println("Mensagem adiantada! (eid " + eid + " vindo de " + sender + ") ");
        /************************* TESTE *************************/

        if (stateManager.getWaiting() == -1) {

            stateManager.addEID(sender, eid);

            /************************* TESTE *************************/
            System.out.println("Nao estou a espera");
            System.out.println("Numero de mensagens recebidas para este EID de replicas diferentes: " + stateManager.moreThenF_EIDs(eid));
            /************************* TESTE *************************/

            if (stateManager.getLastEID() < eid && stateManager.moreThenF_EIDs(eid)) {

                /************************* TESTE *************************/
                System.out.println("Recebi mais de " + conf.getF() + " mensagens para eid " + eid + " que sao posteriores a " + stateManager.getLastEID());
                /************************* TESTE *************************/

                stateManager.setLastEID(eid);
                stateManager.setWaiting(eid - 1);
                //stateManager.emptyReplicas(eid);// isto causa uma excepcao

                SMMessage smsg = new SMMessage(me, eid - 1, TOMUtil.SM_REQUEST, null);
                communication.send(otherAcceptors, smsg);

                /************************* TESTE *************************/

                System.out.println("Enviei um pedido!");
                System.out.println("Quem envia: " + smsg.getSender());
                System.out.println("Que tipo: " + smsg.getType());
                System.out.println("Que EID: " + smsg.getEid());
                System.out.println("Ultimo EID: " + stateManager.getLastEID());
                System.out.println("A espera do EID: " + stateManager.getWaiting());
                /************************* TESTE *************************/
            }
        }
        /************************* TESTE *************************/
        System.out.println("[/TOMLayer.requestState]");
        /************************* TESTE *************************/
    }

    public void SMRequestDeliver(SMMessage msg) {

        lockState.lock();

        /************************* TESTE *************************/
        System.out.println("[TOMLayer.SMRequestDeliver]");
        System.out.println("Recebi um pedido de estado!");
        System.out.println("Estado pedido: " + msg.getEid());
        System.out.println("Checkpoint q eu tenho: " + stateManager.getLog().getLastCheckpointEid());
        System.out.println("Ultimo eid q recebi no log: " + stateManager.getLog().getLastEid());
        /************************* TESTE *************************/
        
        TransferableState state = stateManager.getLog().getTransferableState(msg.getEid());

        lockState.unlock();

        if (state == null) {
            /************************* TESTE *************************/
            System.out.println("Nao tenho o estado pedido!");
            /************************* TESTE *************************/
            state = new TransferableState();
        }

        int[] targets = { msg.getSender() };
        SMMessage smsg = new SMMessage(execManager.getProcessId(), msg.getEid(), TOMUtil.SM_REPLY, state);
        communication.send(targets, smsg);

        /************************* TESTE *************************/
        System.out.println("Quem envia: " + smsg.getSender());
        System.out.println("Que tipo: " + smsg.getType());
        System.out.println("Que EID: " + smsg.getEid());
        //System.exit(0);
        /************************* TESTE *************************/
        /************************* TESTE *************************/
        System.out.println("[/TOMLayer.SMRequestDeliver]");
       /************************* TESTE *************************/
    }

    public void SMReplyDeliver(SMMessage msg) {

        /************************* TESTE *************************/
        System.out.println("[TOMLayer.SMReplyDeliver]");
        System.out.println("Recebi uma resposta de uma replica!");
        System.out.println("[reply] Esta resposta tem o estado? " + msg.getState().hasState());
        System.out.println("[reply] EID do ultimo checkpoint: " + msg.getState().getLastCheckpointEid());
        System.out.println("[reply] EID do ultimo batch recebido: " + msg.getState().getLastEid());
        if (msg.getState().getMessageBatches() != null)
            System.out.println("[reply] Numero de batches: " + msg.getState().getMessageBatches().length);
        else System.out.println("[reply] Nao ha batches");
        if (msg.getState().getState() != null) {
            System.out.println("[reply] Tamanho do estado em bytes: " + msg.getState().getState().length);

            int value = 0;
            for (int i = 0; i < 4; i++) {
                int shift = (4 - 1 - i) * 8;
                value += (msg.getState().getState()[i] & 0x000000FF) << shift;
            }
            System.out.println("[reply] Valor do estado: " + value);
        }
        else System.out.println("[reply] Nao ha estado");
        /************************* TESTE *************************/

        if (stateManager.getWaiting() != -1 && msg.getEid() == stateManager.getWaiting()) {

            /************************* TESTE *************************/
            System.out.println("A resposta e referente ao eid que estou a espera! (" + msg.getEid() + ")");
            /************************* TESTE *************************/

            stateManager.addState(msg.getSender(),msg.getState());

            if (stateManager.moreThenF_Replies()) {

                /************************* TESTE *************************/
                System.out.println("Ja tenho mais que " + conf.getF() + " respostas iguais!");
                /************************* TESTE *************************/

                TransferableState state = stateManager.getValidState();
                
                if (state != null) {

                    /************************* TESTE *************************/
                    System.out.println("As respostas desse estado são validas!");

                    System.out.println("[state] Esta resposta tem o estado? " + state.hasState());
                    System.out.println("[state] EID do ultimo checkpoint: " + state.getLastCheckpointEid());
                    System.out.println("[state] EID do ultimo batch recebido: " + state.getLastEid());
                    if (state.getMessageBatches() != null)
                        System.out.println("[state] Numero de batches: " + state.getMessageBatches().length);
                    else System.out.println("[state] Nao ha batches");
                    if (state.getState() != null) {
                        System.out.println("[state] Tamanho do estado em bytes: " + state.getState().length);

                        int value = 0;
                        for (int i = 0; i < 4; i++) {
                            int shift = (4 - 1 - i) * 8;
                            value += (state.getState()[i] & 0x000000FF) << shift;
                        }
                        System.out.println("[state] Valor do estado: " + value);
                    }
                    else System.out.println("[state] Nao ha estado");

                    //System.exit(0);
                    /************************* TESTE *************************/

                    lockState.lock();
                    stateManager.getLog().update(state);

                    /************************* TESTE *************************/
                    System.out.println("[log] Estado pedido: " + msg.getEid());
                    System.out.println("[log] EID do ultimo checkpoint: " + stateManager.getLog().getLastCheckpointEid());
                    System.out.println("[log] EID do ultimo batch recebido: " + stateManager.getLog().getLastEid());
                    System.out.println("[log] Numero de batches: " + stateManager.getLog().getNumBatches());
                    if (stateManager.getLog().getState() != null) {
                        System.out.println("[log] Tamanho do estado em bytes: " + stateManager.getLog().getState().length);
                    
                        int value = 0;
                        for (int i = 0; i < 4; i++) {
                            int shift = (4 - 1 - i) * 8;
                            value += (stateManager.getLog().getState()[i] & 0x000000FF) << shift;
                        }
                        System.out.println("[log] Valor do estado: " + value);
                    }
                    //System.exit(0);
                    /************************* TESTE *************************/

                    lockState.unlock();

                    System.out.println("Desbloqueei o lock para o log do estado");
                    dt.deliverLock();

                    System.out.println("Bloqueei o lock entre esta thread e a delivery thread");

                    ot.OutOfContextLock();

                    System.out.println("Bloqueei o lock entre esta thread e a out of context thread");

                    stateManager.setWaiting(-1);

                    System.out.println("Ja nao estou a espera de nenhum estado, e vou actualizar-me");

                    dt.update(state);

                    dt.canDeliver();

                    ot.OutOfContextUnlock();
                    dt.deliverUnlock();
                    
                    stateManager.emptyStates();


                } else if ((conf.getN() / 2) < stateManager.getReplies()) {
                    
                    /************************* TESTE *************************/
                    System.out.println("Tenho mais de 2F respostas que nao servem para nada!");
                    //System.exit(0);
                    /************************* TESTE *************************/

                    stateManager.setWaiting(-1);
                    stateManager.emptyStates();
                }
            }
        }
        /************************* TESTE *************************/
        System.out.println("[/TOMLayer.SMReplyDeliver]");
        /************************* TESTE *************************/
    }

    public boolean isRetrievingState() {
        return stateManager != null && stateManager.getWaiting() != -1;
    }

    public void setNoExec() {
        Logger.println("(TOMLayer.setInExec_Update) modifying inExec from " + this.inExecution + " to " + -1);

        proposeLock.lock();
        this.inExecution = -1;
        //ot.addUpdate();
        canPropose.signalAll();
        proposeLock.unlock();
    }

    /********************************************************/
}
