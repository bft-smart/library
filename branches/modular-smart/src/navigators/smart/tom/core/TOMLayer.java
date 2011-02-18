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

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.clientsmanagement.ClientsManager;
import navigators.smart.clientsmanagement.PendingRequests;
import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.statemanagment.StateLog;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchBuilder;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

/**
 * This class implements a thread that uses the PaW algorithm to provide the application
 * a layer of total ordered messages
 * 
 * TODO There is a bug when we request a statetransfer for eid 0 -> statemanager.waiting is set to -1 then which means we are not waiting for a state
 */
public class TOMLayer implements RequestReceiver {
	
	private static Logger log = Logger.getLogger(TOMLayer.class.getCanonicalName());

    //other components used by the TOMLayer (they are never changed)
    private ServerCommunicationSystem communication; // Communication system between replicas
    private final DeliveryThread dt; // Thread which delivers total ordered messages to the appication
    private TOMConfiguration conf; // TOM configuration
    /** Store requests received but still not ordered */
    public final ClientsManager clientsManager;
    /** Interface to the consensus */
    private ConsensusService consensusService;
    private TOMRequestReceiver receiver;
    //the next two are used to generate message digests
//    private MessageDigest md;
    //the next two are used to generate non-deterministic data in a deterministic way (by the leader)
//    private Random random = new Random();
    private BatchBuilder bb = new BatchBuilder();
//    private long lastTimestamp = 0;
    private MessageDigest md;
    
    private Object requestsync = new Object();

    /* The next fields are used only for benchmarking */
//    private static final int BENCHMARK_PERIOD = 100;
//    private long numMsgsReceived;
//    private Storage stConsensusDuration;
//    private Storage stConsensusBatch;

    /**
     * Creates a new instance of TOMulticastLayer
     * @param receiver Object that receives requests from clients
     * @param cs Communication system between replicas
     * @param conf TOM configuration
     */
    public TOMLayer(TOMReceiver receiver,
            ServerCommunicationSystem cs,
            TOMConfiguration conf) {

        this.receiver = receiver;
        this.communication = cs;
        this.conf = conf;
//        this.numMsgsReceived = 0;
//        this.stConsensusBatch = new Storage(BENCHMARK_PERIOD);
//        this.stConsensusDuration = new Storage(BENCHMARK_PERIOD);

        try {
            this.md = MessageDigest.getInstance("MD5"); // TODO: nao devia ser antes SHA?
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

        this.clientsManager = new ClientsManager(conf); // Create clients manager

        this.dt = new DeliveryThread(this, receiver, conf); // Create delivery thread
        this.dt.start();



        /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS E TRANSFERENCIA DE ESTADO*/
        stateManager = new StateManager(this.conf.getCheckpoint_period(), this.conf.getF(), this.conf.getN(), this.conf.getProcessId());
        /*******************************************************/
    }

    /**
     * Retrieve TOM configuration
     * @return TOM configuration
     */
    public TOMConfiguration getConf() {
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

    /**
     * This method is invoked by the comunication system to deliver a request.
     * It assumes that the communication system delivers the message in FIFO
     * order.
     *
     * @param msg The request being received
     */
    public void requestReceived(TOMMessage msg) {
    	synchronized(requestsync){		//synchronize forwareded and client received msg access
    		// check if this request is valid
    		if (clientsManager.requestReceived(msg, true, !msg.isReadOnlyRequest())) {
    			if (msg.isReadOnlyRequest()) {
    				receiver.receiveMessage(msg);
    			} else {
    				consensusService.notifyNewRequest(msg);
    			}
    		} else {
    			if(log.isLoggable(Level.FINER)) 
    				log.finer(" the received TOMMessage " + msg + " was discarded.");
    		}
    	}
    }

    /**
     * Creates a value to be proposed to the acceptors. Invoked if this replica is the leader
     * @return A value to be proposed to the acceptors
     */
    public byte[] createPropose() {
        // Retrieve a set of pending requests from the clients manager
        PendingRequests pendingRequests = clientsManager.getPendingRequests();

        int numberOfMessages = pendingRequests.size(); // number of messages retrieved
        int numberOfNonces = conf.getNumberOfNonces(); // ammount of nonces to be generated

//        cons.batchSize = numberOfMessages;
        /*
        // These instructions are used only for benchmarking
        stConsensusBatch.store(numberOfMessages);
        if (stConsensusBatch.getCount() % BENCHMARK_PERIOD == 0) {
        System.out.println("#Media de batch dos ultimos " + BENCHMARK_PERIOD + " consensos: " + stConsensusBatch.getAverage(true));
        stConsensusBatch.reset();
        }
         */
        if(log.isLoggable(Level.FINER) && pendingRequests.size()>0) {
            log.finer(" creating a PROPOSE with " + numberOfMessages + " msgs from " + pendingRequests.getFirst() +" to "+pendingRequests.getLast());
        }

        int totalMessageSize = 0; //total size of the messages being batched
        byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
        byte[][] signatures = null;
        if(conf.getUseSignatures() == 1 ){
        	signatures = new byte[numberOfMessages][]; //bytes of the message (or its hash)
        }

        // Fill the array of bytes for the messages/signatures being batched
        int i = 0;
        for (Iterator<TOMMessage> li = pendingRequests.iterator(); li.hasNext(); i++) {
            TOMMessage msg = li.next();
            if(log.isLoggable(Level.FINEST)){
            	log.finest(" adding req " + msg + " to PROPOSE");}
            messages[i] = msg.getBytes();
            if(conf.getUseSignatures() == 1){
            	signatures[i] = msg.serializedMessageSignature;
            }

            totalMessageSize += messages[i].length;
        }

        // return the batch
        return bb.createBatch(System.currentTimeMillis(), numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
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
    public void decided(Consensus<TOMMessage[]> cons) {
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
    public TOMMessage[] checkProposedValue(byte[] proposedValue) {
        if(log.isLoggable(Level.FINER)) {
            log.finer(" starting");
        }
        BatchReader batchReader = new BatchReader(proposedValue, conf.getUseSignatures() == 1,conf.getSignatureSize());

        TOMMessage[] requests = null;

        try {
            //deserialize the message
            //TODO: verify Timestamps and Nonces
            requests = batchReader.deserialiseRequests();

            //log.finer(" Waiting for clientsManager lock");
            //clientsManager.getClientsLock().lock();
            //log.finer(" Got clientsManager lock");
            for (int i = 0; i < requests.length; i++) {
                //notifies the client manager that this request was received and get
                //the result of its validation
                if (!clientsManager.requestReceived(requests[i], false)) {
                    clientsManager.getClientsLock().unlock();
                    if(log.isLoggable(Level.FINER)) {
                        log.finer(" finished, return=false");
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            clientsManager.getClientsLock().unlock();
            if(log.isLoggable(Level.FINER)) {
                log.finer(" finished, return=false");
            }
            return null;
        }
        //clientsManager.getClientsLock().unlock();
        if(log.isLoggable(Level.FINER)) {
            log.finer(" finished, return=true");
        }

        return requests;
    }
//    /**
//     * TODO: este metodo nao e usado. Pode desaparecer?
//     * @param br
//     * @return
//     */
//    public final boolean verifyTimestampAndNonces(BatchReader br) {
//        long timestamp = br.getTimestamp();
//
//        if (conf.canVerifyTimestamps()) {
//            //br.ufsc.das.util.tom.log.finer(" verifying timestamp "+timestamp+">"+lastTimestamp+"?");
//            if (timestamp > lastTimestamp) {
//                lastTimestamp = timestamp;
//            } else {
//                System.err.println("########################################################");
//                System.err.println("- timestamp received " + timestamp + " <= " + lastTimestamp);
//                System.err.println("- maybe the proposer have a non-synchronized clock");
//                System.err.println("########################################################");
//                return false;
//            }
//        }
//
//        return br.getNumberOfNonces() == conf.getNumberOfNonces();
//    }
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    private StateManager stateManager = null;
    private ReentrantLock lockState = new ReentrantLock();
    
    
	/**
	 * Saves the state to the statelog
	 * @param lastEid The last executionid that is in this state
	 * @param decisionRound The round of the last execution in this state
	 * @param leader The leader of the last execution in this state
	 * @param lmstate The current state of the leadermodule
	 */	
    public void saveState( long lastEid, int decisionRound, int leader, byte[] lmstate) {
    	
    	byte[] state = receiver.getState();

        StateLog statelog = stateManager.getLog();

        lockState.lock();

        if(log.isLoggable(Level.FINER))
            log.finer(" Saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        statelog.newCheckpoint(lastEid, decisionRound, leader, -1, state, computeHash(state), lmstate);

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

        if(log.isLoggable(Level.FINER))
            log.finer(" Finished saving state of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    public void saveBatch(byte[] batch, long lastEid, int decisionRound, int leader) {

        StateLog statelog = stateManager.getLog();

        lockState.lock();

        if(log.isLoggable(Level.FINER))
            log.finer(" Saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);

        statelog.addMessageBatch(batch, decisionRound, leader);
        statelog.setLastEid(lastEid);

        /************************* TESTE *************************
        System.out.println("[TOMLayer.saveBatch]");
        byte[][] batches = statelog.getMessageBatches();
        int count = 0;
        for (int i = 0; i < batches.length; i++)
        if (batches[i] != null) count++;

        System.out.println("//////////////////////BATCH///////////////////////");
        //System.out.println("Total batches (according to StateManager): " + stateManager.getLog().getNumBatches());
        System.out.println("Total batches (actually counted by this code): " + count);
        System.out.println("Ultimo EID: " + statelog.getLastEid());
        //System.out.println("Espaco restante para armazenar batches: " + (stateManager.getLog().getMessageBatches().length - count));
        System.out.println("//////////////////////////////////////////////////");
        System.out.println("[/TOMLayer.saveBatch]");
        /************************* TESTE *************************/
        lockState.unlock();

        if(log.isLoggable(Level.FINER))
            log.finer(" Finished saving batch of EID " + lastEid + ", round " + decisionRound + " and leader " + leader);
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    public void requestStateTransfer(int me, int[] otherAcceptors, int sender, long eid) {

        /************************* TESTE *************************
        System.out.println("[TOMLayer.requestState]");
        System.out.println("Mensagem adiantada! (eid " + eid + " vindo de " + sender + ") ");
        /************************* TESTE *************************/
        if (conf.isStateTransferEnabled()) {
            if (stateManager.getWaiting() == -1) {

                if(log.isLoggable(Level.FINER))
                    log.finer(" I'm not waiting for any state, so I will keep record of this message");
                stateManager.addEID(sender, eid);

                /************************* TESTE *************************
                System.out.println("Nao estou a espera");
                System.out.println("Numero de mensagens recebidas para este EID de replicas diferentes: " + stateManager.moreThenF_EIDs(eid));
                /************************* TESTE *************************/
                if (stateManager.getLastEID() < eid && stateManager.moreThenF_EIDs(eid)) {

                    if(log.isLoggable(Level.FINE))
                        log.fine(" I have now more than " + conf.getF() + " messages for EID " + eid + " which are beyond EID " + stateManager.getLastEID() + " - initialising statetransfer");
                    /************************* TESTE *************************
                    System.out.println("Recebi mais de " + conf.getF() + " mensagens para eid " + eid + " que sao posteriores a " + stateManager.getLastEID());
                    /************************* TESTE *************************/
                    stateManager.setLastEID(eid);
                    stateManager.setWaiting(eid - 1);
                    //stateManager.emptyReplicas(eid);// isto causa uma excepcao

                    SMMessage smsg = new SMMessage(me, eid - 1, TOMUtil.SM_REQUEST, stateManager.getReplica(), null);
                    communication.send(otherAcceptors, smsg);

                    if(log.isLoggable(Level.FINER))
                        log.finer(" I just sent a request to the other replicas for the state up to EID " + (eid - 1));
                    /************************* TESTE *************************

                    System.out.println("Enviei um pedido!");
                    System.out.println("Quem envia: " + smsg.getSender());
                    System.out.println("Que tipo: " + smsg.getType());
                    System.out.println("Que EID: " + smsg.getEid());
                    System.out.println("Ultimo EID: " + stateManager.getLastEID());
                    System.out.println("A espera do EID: " + stateManager.getWaiting());
                    /************************* TESTE *************************/
                }
            } else {
            	log.fine("I'm already waiting for a state - not starting state transfer");
            }
        } else {
        	if(log.isLoggable(Level.WARNING))
                log.warning(" The state transfer protocol is disabled: /n"+
                "################################################################################## /n"+
                "- Ahead-of-time message discarded/n"+
                "- If many messages of the same consensus are discarded, the replica can halt!/n"+
                "- Try to increase the 'system.paxos.highMarc' configuration parameter./n"+
                "- Last consensus executed: " + consensusService.getLastExecuted()+"/n"+
                "##################################################################################");
        }
        /************************* TESTE *************************
        log.finer("[/TOMLayer.requestState]");
        /************************* TESTE *************************/
    }

    public void SMRequestDeliver(SMMessage msg) {

        if (conf.isStateTransferEnabled()) {

            lockState.lock();

                
            /************************* TESTE *************************
            System.out.println("[TOMLayer.SMRequestDeliver]");
            System.out.println("Recebi um pedido de estado!");
            System.out.println("Estado pedido: " + msg.getEid());
            System.out.println("Checkpoint q eu tenho: " + stateManager.getLog().getLastCheckpointEid());
            System.out.println("Ultimo eid q recebi no log: " + stateManager.getLog().getLastEid());
            /************************* TESTE *************************/
            boolean sendState = msg.getReplica() == conf.getProcessId();
            if (log.isLoggable(Level.FINE)) { 
				if (sendState)
					log.fine(" Received " + msg + " - sending full state");
				else
					log.fine(" Received " + msg + " - sending hash");
			}

            TransferableState state = stateManager.getLog().getTransferableState(msg.getEid(), sendState);

            lockState.unlock();

            if (state == null) {
                if(log.isLoggable(Level.FINE))
                    log.fine(" I don't have the state requested :-(");
                /************************* TESTE *************************
                System.out.println("Nao tenho o estado pedido!");
                /************************* TESTE *************************/
                state = new TransferableState();
            }

            /** CODIGO MALICIOSO, PARA FORCAR A REPLICA ATRASADA A PEDIR O ESTADO A OUTRA DAS REPLICAS *
            byte[] badState = {127};
            if (sendState && conf.getProcessId() == 0) state.setState(badState);
            /*******************************************************************************************/
            int[] targets = {msg.getSender()};
            SMMessage smsg = new SMMessage(consensusService.getId(), msg.getEid(), TOMUtil.SM_REPLY, -1, state);
            communication.send(targets, smsg);

            if(log.isLoggable(Level.FINE))
                log.fine(" I sent the state for checkpoint " + state.lastCheckpointEid + " with batches until EID " + state.lastEid);
            /************************* TESTE *************************
            System.out.println("Quem envia: " + smsg.getSender());
            System.out.println("Que tipo: " + smsg.getType());
            System.out.println("Que EID: " + smsg.getEid());
            //System.exit(0);
            /************************* TESTE *************************/
            /************************* TESTE *************************
            System.out.println("[/TOMLayer.SMRequestDeliver]");
            /************************* TESTE *************************/
        }
    }

    public void SMReplyDeliver(SMMessage msg) {

        /************************* TESTE *************************
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
        if (conf.isStateTransferEnabled()) {

            if(log.isLoggable(Level.FINER)){
                log.finer(" The state transfer protocol is enabled");
                log.finer(" I received a state reply for EID " + msg.getEid() + " from replica " + msg.getSender());
            }

            if (stateManager.getWaiting() != -1 && msg.getEid() == stateManager.getWaiting()) {

                /************************* TESTE *************************
                System.out.println("A resposta e referente ao eid que estou a espera! (" + msg.getEid() + ")");
                /************************* TESTE *************************/
                if(log.isLoggable(Level.FINER))
                    log.finer(" The reply is for the EID that I want!");

                if (msg.getSender() == stateManager.getReplica() && msg.getState().state != null) {
                    if(log.isLoggable(Level.FINER))
                        log.finer(" I received the state, from the replica that I was expecting");
                    stateManager.setReplicaState(msg.getState().state);
                }

                stateManager.addState(msg.getSender(), msg.getState());

                if (stateManager.moreThenF_Replies()) {

                    if(log.isLoggable(Level.FINE))
                        log.fine(" I have more than " + conf.getF() + " equal replies!");
                    /************************* TESTE *************************
                    System.out.println("Ja tenho mais que " + conf.getF() + " respostas iguais!");
                    /************************* TESTE *************************/
                    TransferableState state = stateManager.getValidState();

                    int haveState = 0;
                    if (stateManager.getReplicaState() != null) {
                        byte[] hash = null;
                        hash = computeHash(stateManager.getReplicaState());
                        if (state != null) {
                            if (Arrays.equals(hash, state.stateHash)) {
                                haveState = 1;
                            } else {
                                haveState = -1;
                            }
                        }
                    }

                    if (state != null && haveState == 1) {

                        /************************* TESTE *************************
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
                        if(log.isLoggable(Level.FINE))
                            log.fine(" The state of those replies is good!");

                        lockState.lock();

                        stateManager.getLog().update(msg.getState());

                        /************************* TESTE *************************
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

                        //System.out.println("Desbloqueei o lock para o log do estado");
//                        dt.deliverLock();

                        //System.out.println("Bloqueei o lock entre esta thread e a delivery thread");

//                        ot.OutOfContextLock();

                        //System.out.println("Bloqueei o lock entre esta thread e a out of context thread");


                        //System.out.println("Ja nao estou a espera de nenhum estado, e vou actualizar-me");

                        dt.update(state);

//                      dt.canDeliver();

//                      ot.OutOfContextUnlock();
//                      dt.deliverUnlock();

                        stateManager.setWaiting(-1);
                        stateManager.emptyStates();
                        stateManager.setReplicaState(null);

                    } else if (state == null && (conf.getN() / 2) < stateManager.getReplies()) {

                        if(log.isLoggable(Level.FINE))
                            log.fine(" I have more than " + (conf.getN() / 2) + " messages that are no good!");
                        /************************* TESTE *************************
                        System.out.println("Tenho mais de 2F respostas que nao servem para nada!");
                        //System.exit(0);
                        /************************* TESTE *************************/
                        stateManager.setWaiting(-1);
                        stateManager.emptyStates();
                        stateManager.setReplicaState(null);
                    } else if (haveState == -1) {

                        if(log.isLoggable(Level.FINE))
                            log.fine(" The replica from which I expected the state, sent one which doesn't match the hash of the others, or it never sent it at all");

                        stateManager.setWaiting(-1);
                        stateManager.changeReplica();
                        stateManager.emptyStates();
                        stateManager.setReplicaState(null);
                    }
                }
            }
        }
        /************************* TESTE *************************
        System.out.println("[/TOMLayer.SMReplyDeliver]");
        /************************* TESTE *************************/
    }

    public boolean isRetrievingState() {
        return stateManager != null && stateManager.getWaiting() != -1;
    }

    public void setConsensusService(ConsensusService manager) {
        this.consensusService = manager;
        dt.setConsensusservice(consensusService);
    }

    public boolean hasPendingRequests() {
        return clientsManager.hasPendingRequests();
    }
    /********************************************************/

	public byte[] getState() {
		return receiver.getState();
	}
}
