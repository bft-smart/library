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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class implements a thread which will deliver totally ordered requests to the application
 * 
 */
public class DeliveryThread extends Thread {
	
	private static final Logger log = Logger.getLogger(DeliveryThread.class.getCanonicalName());

    private LinkedBlockingQueue<Consensus<TOMMessage[]>> decided = new LinkedBlockingQueue<Consensus<TOMMessage[]>>(); // decided consensus
    private TOMLayer tomLayer; // TOM layer
    private TOMConfiguration conf;

    private final TOMReceiver receiver;
    
    private ConsensusService consensusservice;

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param recv The receiver of the decided requests
     * @param conf TOM configuration
     * @param consensus The consensusservice that decides the order
     */
    public DeliveryThread(TOMLayer tomLayer, TOMReceiver recv, TOMConfiguration conf) {
        super("Delivery Thread "+ conf.getProcessId());
        this.tomLayer = tomLayer;
        this.conf = conf;
        this.receiver = recv;
    }

    /**
     * Invoked by the TOM layer, to deliver a decide consensus
     * @param cons MeasuringConsensus established as being decided
     */
    public void delivery(Consensus<TOMMessage[]> cons) {
        try {
            decided.put(cons);
            if(log.isLoggable(Level.FINER))
                log.finer(" Consensus " + cons.getId() + " finished. decided size=" + decided.size());
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    
    private ReentrantLock deliverLock = new ReentrantLock();
    private Condition canDeliver = deliverLock.newCondition();

    public void deliverLock() {
        deliverLock.lock();
//        if(log.isLoggable(Level.FINER))
//            log.finer("Deliver lock obtained");
    }

    public void deliverUnlock() {
        deliverLock.unlock();
//        if(log.isLoggable(Level.FINER))
//            log.finer("Deliver Released");
    }

    public void canDeliver() {
        canDeliver.signalAll();
    }
    public void update(TransferableState transferredState) {

        deliverLock.lock();

        consensusservice.startDeliverState();
        consensusservice.deliverState(transferredState);

        long lastCheckpointEid = transferredState.lastCheckpointEid;
        long lastEid = transferredState.lastEid;

        if(log.isLoggable(Level.FINE))
            log.fine("I'm going to update myself from EID " + lastCheckpointEid + " to EID " + lastEid);

        receiver.setState(transferredState.state);

//        lm.addLeaderInfo(lastCheckpointEid, state.getLastCheckpointRound(), state.getLastCheckpointLeader());

        for (long eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {

            try {

                byte[] batch = transferredState.getMessageBatch(eid).batch; // take a batch

//                lm.addLeaderInfo(eid, state.getMessageBatch(eid).round, state.getMessageBatch(eid).leader);

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(batch, conf.getUseSignatures()==1,conf.getSignatureSize());

                if(log.isLoggable(Level.FINEST))
                    log.finest("interpreting and verifying batched requests.");

                TOMMessage[] requests = batchReader.deserialiseRequests();

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {

                    /******* Deixo isto comentado, pois nao me parece necessario      **********/
                    /******* Alem disso, esta informacao nao vem no TransferableState **********

                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;

                    /***************************************************************************/
                    tomLayer.clientsManager.requestOrdered(requests[i]);
                    consensusservice.notifyRequestDecided(requests[i]);
                    receiver.receiveOrderedMessage(requests[i]);
                }

                /****** Julgo que isto nao sera necessario ***********
                if (conf.getCheckpoint_period() > 0) {
                    if ((eid > 0) && (eid % conf.getCheckpoint_period() == 0)) {
                        log.finer("Performing checkpoint for consensus " + eid);
                        byte[] state2 = receiver.getState();
                        tomLayer.saveState(state2, eid);
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                    else {
                        log.finer("Storing message batch in the state log for consensus " + eid);
                        tomLayer.saveBatch(batch, eid);
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                }
                */
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }

        }

        decided.clear();

        if(log.isLoggable(Level.FINE))
            log.fine("All finished from " + lastCheckpointEid + " to " + lastEid);
        //verify if there is a next proposal to be executed
        //(it only happens if the previous consensus were decided in a
        //round > 0
        /** Nao consigo perceber se isto tem utilidade neste contexto *****/
        //int nextExecution = lastEid + 1;
        //if(tomLayer.acceptor.executeAcceptedPendent(nextExecution)) {
        //log.finer("Executed setProposal for " + nextExecution);
        //}
        /******************************************************************/

        canDeliver.signalAll();
        deliverLock.unlock();
    }
    
    /********************************************************/

    /**
     * This is the code for the thread. It delivers decided consensus to the TOM request receiver object (which is the application)
     */
    @Override
    public void run() {

        long startTime;
        while (true) {

            /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            deliverLock();

            //if (tomLayer != null) {
                while (tomLayer.isRetrievingState()) {
                    canDeliver.awaitUninterruptibly();
                }
            //}
            /******************************************************************/

            try {
                
                //MeasuringConsensus cons = decided.take(); // take a decided consensus
                /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
//                if(log.isLoggable(Level.FINER))
//                    log.finer("Waiting for a consensus to be delivered.");
                Consensus<TOMMessage[]> cons = decided.poll(1500, TimeUnit.MILLISECONDS); // take a decided consensus
                if (cons == null) {
//                    if(log.isLoggable(Level.FINER))
//                        log.finer("Timeout while waiting for a consensus, starting over.");
                    deliverUnlock();
                    continue;
                }

                if(log.isLoggable(Level.FINER))
                    log.finer("" + cons + " was delivered.");
                /******************************************************************/
                startTime = System.currentTimeMillis();

                //TODO: avoid the case in which the received valid proposal is
                //different from the decided value

                TOMMessage[] requests = cons.getDeserializedDecision();

                if (requests == null) {
                    if(log.isLoggable(Level.FINER))
                        log.finer("interpreting and verifying batched requests.");

                    // obtain an array of requests from the taken consensus
                    BatchReader batchReader = new BatchReader(cons.getDecision(), conf.getUseSignatures()==1,conf.getSignatureSize());
                    requests = batchReader.deserialiseRequests();

                } else {
                    if(log.isLoggable(Level.FINER))
                        log.finer("using cached requests from the propose.");

                }
                tomLayer.clientsManager.getClientsLock().lock();
                for (int i = 0; i < requests.length; i++) {

                    /** ISTO E CODIGO DO JOAO, PARA TRATAR DE DEBUGGING */
//                    if (Logger.debug)
//                        requests[i].setSequence(new DebugInfo(cons.getId(), cons.getDecisionRound(), lm.getLeader(cons.getId(), cons.getDecisionRound())));
                    /****************************************************/

//                    requests[i].consensusStartTime = cons.startTime;
//                    requests[i].consensusExecutionTime = cons.executionTime;
//                    requests[i].consensusBatchSize = cons.batchSize;
                    tomLayer.clientsManager.requestOrdered(requests[i]);
                    consensusservice.notifyRequestDecided(requests[i]);
                }
                tomLayer.clientsManager.getClientsLock().unlock();

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {
//                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;
                    receiver.receiveOrderedMessage(requests[i]);
                }

                
                /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */

                if(log.isLoggable(Level.FINER))
                    log.finer("I just delivered the batch of EID " + cons.getId());

                if (conf.isStateTransferEnabled()) {
                    if(log.isLoggable(Level.FINER))
                        log.finer("The state transfer protocol is enabled");
                    if (conf.getCheckpoint_period() > 0) {
                        if ((cons.getId() > 0) && ((cons.getId() % conf.getCheckpoint_period()) == 0)) {
                            if(log.isLoggable(Level.FINER))
                                log.finer("Performing checkpoint for consensus " + cons.getId());
                            tomLayer.saveState( cons.getId(), cons.getDecisionRound(), consensusservice.getProposer(cons),consensusservice.getState(cons));
                            //TODO: possivelmente fazer mais alguma coisa
                        }
                        else {
                            if(log.isLoggable(Level.FINER))
                                    log.finer("Storing message batch in the state log for consensus " + cons.getId());
                            tomLayer.saveBatch(cons.getDecision(), cons.getId(), cons.getDecisionRound(), consensusservice.getProposer(cons));
                            //TODO: possivelmente fazer mais alguma coisa
                        }
                    }
                }
                /********************************************************/
                
                consensusservice.deliveryFinished(cons);
                if(log.isLoggable(Level.FINER))
                        log.finer("(DeliveryThread.run) All finished for " + cons.getId() + ", took " + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            deliverUnlock();
            /******************************************************************/

        }
    }

    public void setConsensusservice(ConsensusService consensusservice) {
        this.consensusservice = consensusservice;
    }
}
