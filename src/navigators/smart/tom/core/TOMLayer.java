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

import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignedObject;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import navigators.smart.clientsmanagement.ClientsManager;
import navigators.smart.clientsmanagement.RequestList;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.paxosatwar.Consensus;
import navigators.smart.paxosatwar.executionmanager.TimestampValuePair;
import navigators.smart.paxosatwar.executionmanager.Execution;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.Round;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.messages.TOMMessageType;
import navigators.smart.tom.core.timer.RequestsTimer;
import navigators.smart.tom.core.timer.ForwardedMessage;
import navigators.smart.tom.util.BatchBuilder;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;
import navigators.smart.tom.leaderchange.LCMessage;
import navigators.smart.tom.leaderchange.CollectData;
import navigators.smart.tom.leaderchange.LCManager;
import navigators.smart.tom.leaderchange.LastEidData;


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

    /*** ISTO E CODIGO DO JOAO, RELACIONADO COM A TROCA DE LIDER */
    private LCManager lcManager;
    /*************************************************************/

    /* flag that indicates that the lader changed between the last propose and
    this propose. This flag is changed on updateLeader (to true) and decided
    (to false) and used in run.*/
    private boolean leaderChanged = true;

    private PrivateKey prk;
    
    private ServerViewManager reconfManager;
    
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
            TOMReceiver receiver,
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
            this.md = MessageDigest.getInstance("MD5"); // TODO: nao devia ser antes SHA?
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        try {
            this.engine = Signature.getInstance("SHA1withRSA");
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }

        this.prk = reconfManager.getStaticConf().getRSAPrivateKey();

        /*** ISTO E CODIGO DO JOAO, RELACIONADO COM A TROCA DE LIDER */
        this.lcManager = new LCManager(this,recManager, md);
        /*************************************************************/

        this.dt = new DeliveryThread(this, receiver, this.reconfManager); // Create delivery thread
        this.dt.start();

        /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS E TRANSFERENCIA DE ESTADO*/
        this.stateManager = new StateManager(this.reconfManager, this, dt, lcManager, execManager);
        /*******************************************************/
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
     * @param sender Replica id that supposably signed this object
     * @return True if the signature is valid, false otherwise
     */
    public boolean verifySignature(SignedObject so, int sender) {
        try {
            return so.verify(reconfManager.getStaticConf().getRSAPublicKey(sender), engine);
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
     * This method is invoked by the comunication system to deliver a request.
     * It assumes that the communication system delivers the message in FIFO
     * order.
     *
     * @param msg The request being received
     */
    @Override
    public void requestReceived(TOMMessage msg) {
        // check if this request is valid and add it to the client' pending requests list
        boolean readOnly = (msg.getReqType() == TOMMessageType.READONLY_REQUEST);
        if (clientsManager.requestReceived(msg, true, !readOnly, communication)) {
            if (readOnly) {
                dt.deliverUnordered(msg, lcManager.getLastReg());
            } else {
                messagesLock.lock();
                haveMessages.signal();
                messagesLock.unlock();
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
        Logger.println("Running."); // TODO: isto n podia passar para fora do ciclo?
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

                execManager.getProposer().startExecution(execId,
                        createPropose(execManager.getExecution(execId).getLearner()));
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
    public TOMMessage[] checkProposedValue(byte[] proposedValue) {
        Logger.println("(TOMLayer.isProposedValueValid) starting");

        BatchReader batchReader = new BatchReader(proposedValue, 
                this.reconfManager.getStaticConf().getUseSignatures() == 1);

        TOMMessage[] requests = null;

        try {
            //deserialize the message
            //TODO: verify Timestamps and Nonces
            requests = batchReader.deserialiseRequests(this.reconfManager);

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
            System.out.println("(TOMLayer.forwardRequestToLeader) forwarding " + request + " to " + leaderId);
            communication.send(new int[]{leaderId}, 
                new ForwardedMessage(this.reconfManager.getStaticConf().getProcessId(), request));
        }
            //******* EDUARDO END **************//
    }

    public boolean isRetrievingState() {
        //lockTimer.lock();
        boolean result =  stateManager != null && stateManager.getWaiting() != -1;
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

    /*** ISTO E CODIGO DO JOAO, RELACIONADO COM A TROCA DE LIDER */

    /**
     * Este metodo e invocado quando ha um timeout e o request ja foi re-encaminhado para o lider
     * @param requestList Lista de pedidos que a replica quer ordenar mas nao conseguiu
     */
    public void triggerTimeout(List<TOMMessage> requestList) {

        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        
        requestsTimer.stopTimer();
        requestsTimer.Enabled(false);
                
        // ainda nao estou na fase de troca de lider?

        if (lcManager.getNextReg() == lcManager.getLastReg()) {
            
                Logger.println("(TOMLayer.triggerTimeout) initialize synch phase");
               
                lcManager.setNextReg(lcManager.getLastReg() + 1); // definir proximo timestamp

                int regency = lcManager.getNextReg();


                // guardar mensagens para ordenar
                lcManager.setCurrentRequestTimedOut(requestList);

                // guardar informacao da mensagem que vou enviar
                lcManager.addStop(regency, this.reconfManager.getStaticConf().getProcessId());

                execManager.stop(); // parar execucao do consenso

                try { // serializar conteudo a enviar na mensagem STOP
                    out = new ObjectOutputStream(bos);

                    if (lcManager.getCurrentRequestTimedOut() != null) {

                        //TODO: Se isto estiver a null, e porque nao houve timeout. Fazer o q?
                        byte[] msgs = bb.makeBatch(lcManager.getCurrentRequestTimedOut(), 0, 0, reconfManager);
                        List<TOMMessage> temp = lcManager.getCurrentRequestTimedOut();
                        out.writeBoolean(true);
                        out.writeObject(msgs);
                    }
                    else {
                        out.writeBoolean(false);
                    }

                    byte[] payload = bos.toByteArray();

                    out.flush();
                    bos.flush();

                    out.close();
                    bos.close();
                   
                    // enviar mensagem STOP
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

                evaluateStops(regency); // avaliar mensagens stops

        }

    }

    // este metodo e invocado aquando de um timeout ou da recepcao de uma mensagem STOP
    private void evaluateStops(int nextReg) {

        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        // passar para a fase de troca de lider se já tiver recebido mais de f mensagens
        if (lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorumF() && lcManager.getNextReg() == lcManager.getLastReg()) {

           Logger.println("(TOMLayer.evaluateStops) initialize synch phase");
           requestsTimer.Enabled(false);
            requestsTimer.stopTimer();
            
            lcManager.setNextReg(lcManager.getLastReg() + 1); // definir proximo timestamp

            int regency = lcManager.getNextReg();

            // guardar informacao da mensagem que vou enviar
            lcManager.addStop(regency, this.reconfManager.getStaticConf().getProcessId());

            execManager.stop(); // parar execucao do consenso

            try { // serializar conteudo a enviar na mensagem STOP
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                if (lcManager.getCurrentRequestTimedOut() != null) {

                    //TODO: Se isto estiver a null, e porque nao houve timeout. Fazer o q?
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
                
                // enviar mensagem STOP
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
        // posso passar para a fase de sincronizacao?
        if (lcManager.getStopsSize(nextReg) > this.reconfManager.getQuorum2F() && lcManager.getNextReg() > lcManager.getLastReg()) {

            
            Logger.println("(TOMLayer.evaluateStops) installing regency " + lcManager.getNextReg());
            lcManager.setLastReg(lcManager.getNextReg()); // definir ultimo timestamp

            int regency = lcManager.getLastReg();

            // evitar um memory leak
            lcManager.removeStops(nextReg);
            
            requestsTimer.Enabled(true);
            requestsTimer.startTimer();

            //int leader = regency % this.reconfManager.getCurrentViewN(); // novo lider
            int leader = lcManager.getNewLeader();
            int in = getInExec(); // eid a executar
            int last = getLastExec(); // ultimo eid decidido
            
            lm.setNewReg(regency);
            lm.setNewLeader(leader);
                    
            // Se eu nao for o lider, tenho que enviar uma mensagem STOPDATA para ele
            if (leader != this.reconfManager.getStaticConf().getProcessId()) {

                try { // serializar o conteudo da mensagem SYNC

                    bos = new ByteArrayOutputStream();
                    out = new ObjectOutputStream(bos);

                    if (last > -1) { // conteudo do ultimo eid decidido

                        out.writeBoolean(true);
                        out.writeInt(last);
                        Execution exec = execManager.getExecution(last);
                        byte[] decision = exec.getLearner().getDecision();

                        out.writeObject(decision);

                        // TODO: VAI SER PRECISO METER UMA PROVA!!!

                    }

                    else out.writeBoolean(false);

                    if (in > -1) { // conteudo do eid a executar

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
                    // enviar mensagem SYNC para o novo lider
                    communication.send(b,
                        new LCMessage(this.reconfManager.getStaticConf().getProcessId(), TOMUtil.STOPDATA, regency, payload));

                    //TODO: Voltar a ligar o timeout

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

            } else { // se for o lider, vou guardar a informacao que enviaria na mensagem SYNC

                Logger.println("(TOMLayer.evaluateStops) I'm the leader for this new regency");
                LastEidData lastData = null;
                CollectData collect = null;

                if (last > -1) {  // conteudo do ultimo eid decidido
                    Execution exec = execManager.getExecution(last);
                    byte[] decision = exec.getLearner().getDecision();

                    lastData = new LastEidData(this.reconfManager.getStaticConf().getProcessId(), last, decision, null);
                    // TODO: VAI SER PRECISO METER UMA PROVA!!!

                }
                else lastData = new LastEidData(this.reconfManager.getStaticConf().getProcessId(), last, null, null);

                lcManager.addLastEid(regency, lastData);


                if (in > -1) { // conteudo do eid a executar
                    Execution exec = execManager.getExecution(in);

                    TimestampValuePair quorumWeaks = exec.getQuorumWeaks();
                    HashSet<TimestampValuePair> writeSet = exec.getWriteSet();

                    collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), in, quorumWeaks, writeSet);

                }
                else collect = new CollectData(this.reconfManager.getStaticConf().getProcessId(), -1, new TimestampValuePair(-1, new byte[0]), new HashSet<TimestampValuePair>());

                SignedObject signedCollect = sign(collect);

                lcManager.addCollect(regency, signedCollect);
            }

        }
    }

    /**
     * Este metodo e invocado pelo MessageHandler sempre que receber mensagens relacionados
     * com a troca de lider
     * @param msg Mensagem recebida de outra replica
     */
    public void deliverTimeoutRequest(LCMessage msg) {

        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;

        switch (msg.getType()) {
            case TOMUtil.STOP: // mensagens STOP

                {

                    // esta mensagem e para a proxima mudanca de lider?
                    if (msg.getReg() == lcManager.getLastReg() + 1) {
                        
                        Logger.println("(TOMLayer.deliverTimeoutRequest) received regency change request");
                        try { // descerializar o conteudo da mensagem STOP

                            bis = new ByteArrayInputStream(msg.getPayload());
                            ois = new ObjectInputStream(bis);

                            boolean hasReqs = ois.readBoolean();
                            clientsManager.getClientsLock().lock();

                            if (hasReqs) {

                                // Guardar os pedidos que a outra replica nao conseguiu ordenar
                                //TODO: Os requests  tem q ser verificados!
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

                        // guardar informacao sobre a mensagem STOP
                        lcManager.addStop(msg.getReg(), msg.getSender());

                        evaluateStops(msg.getReg()); // avaliar mensagens stops
                    }
                }
                break;
            case TOMUtil.STOPDATA: // mensagens STOPDATA
                {

                    int regency = msg.getReg();

                    // Sou o novo lider e estou a espera destas mensagem?
                    if (regency == lcManager.getLastReg() &&
                            this.reconfManager.getStaticConf().getProcessId() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {
                        
                        Logger.println("(TOMLayer.deliverTimeoutRequest) I'm the new leader and I received a STOPDATA");
                        //TODO: E preciso verificar a prova do ultimo consenso decidido e a assinatura do estado do consenso actual!

                        LastEidData lastData = null;
                        SignedObject signedCollect = null;

                        int last = -1;
                        byte[] lastValue = null;

                        int in = -1;

                        TimestampValuePair quorumWeaks = null;
                        HashSet<TimestampValuePair> writeSet = null;


                        try { // descerializar o conteudo da mensagem

                            bis = new ByteArrayInputStream(msg.getPayload());
                            ois = new ObjectInputStream(bis);

                            if (ois.readBoolean()) { // conteudo do ultimo eid decidido

                                last = ois.readInt();

                                lastValue = (byte[]) ois.readObject();

                                //TODO: Falta a prova!
                            }

                            lastData = new LastEidData(msg.getSender(), last, lastValue, null);

                            lcManager.addLastEid(regency, lastData);

                            // conteudo do eid a executar

                            signedCollect = (SignedObject) ois.readObject();

                            /*in = ois.readInt();
                            quorumWeaks = (RoundValuePair) ois.readObject();
                            writeSet = (HashSet<RoundValuePair>) ois.readObject();*/



                            /*collect = new CollectData(msg.getSender(), in,
                                    quorumWeaks, writeSet);*/

                            ois.close();
                            bis.close();

                            lcManager.addCollect(regency, signedCollect);

                            int bizantineQuorum = (reconfManager.getCurrentViewN() + reconfManager.getCurrentViewF()) / 2;

                            // ja recebi mensagens de um quorum bizantino,
                            // referentes tanto ao ultimo eid como o actual?s
                            if (lcManager.getLastEidsSize(regency) > bizantineQuorum &&
                                    lcManager.getCollectsSize(regency) > bizantineQuorum) {

                                catch_up(regency);
                            }
                        } catch (IOException ex) {
                            ex.printStackTrace(System.err);
                        } catch (ClassNotFoundException ex) {
                            ex.printStackTrace(System.err);
                        }

                  }
            }
            break;
        case TOMUtil.SYNC: // mensagens SYNC
            {
                int regency = msg.getReg();

                // Estou a espera desta mensagem, e recebi-a do novo lider?
                if (msg.getReg() == lcManager.getLastReg() &&
                        msg.getReg() == lcManager.getNextReg() && msg.getSender() == lm.getCurrentLeader()/*(regency % this.reconfManager.getCurrentViewN())*/) {

                    LastEidData lastHighestEid = null;
                    int currentEid = -1;
                    HashSet<SignedObject> signedCollects = null;
                    byte[] propose = null;
                    int batchSize = -1;

                    try { // descerializar o conteudo da mensagem

                        bis = new ByteArrayInputStream(msg.getPayload());
                        ois = new ObjectInputStream(bis);

                        lastHighestEid = (LastEidData) ois.readObject();
                        currentEid = ois.readInt();
                        signedCollects = (HashSet<SignedObject>) ois.readObject();
                        propose = (byte[]) ois.readObject();
                        batchSize = ois.readInt();
                        
                        lcManager.setCollects(regency, signedCollects);

                        // o predicado sound e verdadeiro?
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

    // este metodo e usado para verificar se o lider pode fazer a mensagem catch-up
    // e tambem envia-la
    private void catch_up(int regency) {

        Logger.println("(TOMLayer.catch_up) verify STOPDATA info");
        ObjectOutputStream out = null;
        ByteArrayOutputStream bos = null;

        LastEidData lastHighestEid = lcManager.getHighestLastEid(regency);

        int currentEid = lastHighestEid.getEid() + 1;
        HashSet<SignedObject> signedCollects = null;
        byte[] propose = null;
        int batchSize = -1;

        // normalizar os collects e aplicar-lhes o predicado "sound"
        if (lcManager.sound(lcManager.selectCollects(regency, currentEid))) {
            
            Logger.println("(TOMLayer.catch_up) sound predicate is true");

            signedCollects = lcManager.getCollects(regency); // todos collects originais que esta replica recebeu

            Consensus cons = new Consensus(-1); // este objecto só serve para obter o batchsize,
                                                    // a partir do codigo que esta dentro do createPropose()

            propose = createPropose(cons);
            batchSize = cons.batchSize;

            try { // serializar a mensagem CATCH-UP
                bos = new ByteArrayOutputStream();
                out = new ObjectOutputStream(bos);

                out.writeObject(lastHighestEid);

                //TODO: Falta serializar a prova!!

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
                            
                // enviar a mensagem CATCH-UP
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

    // este metdo e invocado em todas as replicas, e serve para verificar e aplicar
    // a informacao enviada na mensagem catch-up
    private void finalise(int regency, LastEidData lastHighestEid,
            int currentEid, HashSet<SignedObject> signedCollects, byte[] propose, int batchSize, boolean iAmLeader) {

        Logger.println("(TOMLayer.finalise) final stage of LC protocol");
        int me = this.reconfManager.getStaticConf().getProcessId();
        Execution exec = null;
        Round r = null;

        // Esta replica esta atrasada?
        if (getLastExec() + 1 < lastHighestEid.getEid()) {
            //TODO: Caso em que e necessario aplicar a transferencia de estado

            System.out.println("E PRECISO APLICAR A TRANSFERENCIA DE ESTADO!!");

        } else if (getLastExec() + 1 == lastHighestEid.getEid()) {
        // esta replica ainda esta a executar o ultimo consenso decidido?

            //TODO: e preciso verificar a prova!

            System.out.println("Ainda estou no eid anterior ao mais actual!");
            
            exec = execManager.getExecution(lastHighestEid.getEid());
            r = exec.getLastRound();

            if (r == null) {
                exec.createRound(reconfManager);
            }
            else {
                r.clear();
            }
            
            byte[] hash = computeHash(propose);
            r.propValueHash = hash;
            r.propValue = propose;
            r.deserializedPropValue = checkProposedValue(propose);
            exec.decided(r, hash); // entregar a decisao a delivery thread
        }
        byte[] tmpval = null;

        HashSet<CollectData> selectedColls = lcManager.selectCollects(signedCollects, currentEid);

        // obter um valor que satisfaca o predicado "bind"
        tmpval = lcManager.getBindValue(selectedColls);

        // se tal valor nao existir, obter o valor escrito pelo novo lider
        if (tmpval == null && lcManager.unbound(selectedColls)) {
            Logger.println("(TOMLayer.finalise) did not found a value that might have already been decided");
            tmpval = propose;
        }
        else Logger.println("(TOMLayer.finalise) found a value that might have been decided");

        if (tmpval != null) { // consegui chegar a algum valor?
            
            Logger.println("(TOMLayer.finalise) resuming normal phase");
            lcManager.removeCollects(regency); // evitar memory leaks
            
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
            r.deserializedPropValue = checkProposedValue(tmpval);

            if(exec.getLearner().firstMessageProposed == null)
                exec.getLearner().firstMessageProposed = r.deserializedPropValue[0];
                        
            r.setWeak(me, hash);

            lm.setNewReg(regency);

            // resumir a execucao normal
            execManager.restart();
            //leaderChanged = true;
            setInExec(currentEid);
            if (iAmLeader) {
                Logger.println("(TOMLayer.finalise) wake up proposer thread");
                imAmTheLeader();
            } // acordar a thread que propoem valores na operacao normal

            Logger.println("(TOMLayer.finalise) sending WEAK message");
            //System.out.println(regency + " // WEAK (R: " + r.getNumber() + "): " + Base64.encodeBase64String(r.propValueHash));
            // enviar mensagens WEAK para as outras replicas           
            communication.send(this.reconfManager.getCurrentViewOtherAcceptors(),
                    acceptor.getFactory().createWeak(currentEid, r.getNumber(), r.propValueHash));

        }

        else Logger.println("(TOMLayer.finalise) sync phase failed for regency" + regency);
    }
    /**************************************************************/
}
