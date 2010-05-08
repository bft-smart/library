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
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import navigators.smart.paxosatwar.Consensus;
import navigators.smart.statemanagment.StateManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;


/**
 * This class implements a thread which will deliver totally ordered requests to the application
 * 
 */
public class DeliveryThread extends Thread {

    private LinkedBlockingQueue<Consensus> decided = new LinkedBlockingQueue<Consensus>(); // decided consensus
    private TOMLayer tomLayer; // TOM layer
    private RequestRecover requestRecover; // TODO: isto ainda vai ser usado?
    private TOMRequestReceiver receiver; // Object that receives requests from clients
    private TOMConfiguration conf;

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param receiver Object that receives requests from clients
     * @param conf TOM configuration
     */
    public DeliveryThread(TOMLayer tomLayer, TOMRequestReceiver receiver, TOMConfiguration conf) {
        super("Delivery Thread");

        this.tomLayer = tomLayer;
        this.receiver = receiver;
        this.conf = conf;
        this.requestRecover = new RequestRecover(tomLayer, conf);
    }

    /**
     * Invoked by the TOM layer, to deliver a decide consensus
     * @param cons Consensus established as being decided
     */
    public void delivery(Consensus cons) {
        try {
            decided.put(cons);
            Logger.println("(DeliveryThread.delivery) Consensus " + cons.getId() + " finished. decided size=" + decided.size());
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
    
    private ReentrantLock deliverLock = new ReentrantLock();
    private Condition canDeliver = deliverLock.newCondition();

    public void deliverLock() {
        deliverLock.lock();
        Logger.println("(DeliveryThread.deliverLock) Deliver lock obtained");
    }

    public void deliverUnlock() {
        deliverLock.unlock();
        Logger.println("(DeliveryThread.deliverUnlock) Deliver Released");
    }

    public void canDeliver() {
        canDeliver.signalAll();
    }
    public void update(TransferableState state) {

        //deliverLock.lock();

        int lastCheckpointEid = state.getLastCheckpointEid();
        int lastEid = state.getLastEid();

        Logger.println("(DeliveryThread.update) I'm going to update myself from EID " + lastCheckpointEid + " to EID " + lastEid);

        receiver.setState(state.getState());

        tomLayer.lm.addLeaderInfo(lastCheckpointEid, state.getLastCheckpointRound(), state.getLastCheckpointLeader());

        for (int eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {

            try {

                byte[] batch = state.getMessageBatch(eid).batch; // take a batch

                tomLayer.lm.addLeaderInfo(eid, state.getMessageBatch(eid).round, state.getMessageBatch(eid).leader);

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(batch, conf.getUseSignatures()==1);

                Logger.println("(DeliveryThread.update) interpreting and verifying batched requests.");

                TOMMessage[] requests = batchReader.deserialiseRequests();

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {

                    /******* Deixo isto comentado, pois nao me parece necessario      **********/
                    /******* Alem disso, esta informacao nao vem no TransferableState **********

                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;

                    /***************************************************************************/
                    tomLayer.clientsManager.requestOrdered(requests[i]);
                    receiver.receiveOrderedMessage(requests[i]);
                }

                /****** Julgo que isto nao sera necessario ***********
                if (conf.getCheckpoint_period() > 0) {
                    if ((eid > 0) && (eid % conf.getCheckpoint_period() == 0)) {
                        Logger.println("(DeliveryThread.update) Performing checkpoint for consensus " + eid);
                        byte[] state2 = receiver.getState();
                        tomLayer.saveState(state2, eid);
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                    else {
                        Logger.println("(DeliveryThread.update) Storing message batch in the state log for consensus " + eid);
                        tomLayer.saveBatch(batch, eid);
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                }
                */
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }

        }

        //set this consensus as the last executed
        tomLayer.setLastExec(lastEid);

        //define the last stable consensus... the stable consensus can
        //be removed from the leaderManager and the executionManager
        if (lastEid > 2) {
            int stableConsensus = lastEid - 3;

            //tomLayer.lm.removeStableMultipleConsenusInfos(lastCheckpointEid, stableConsensus);
            tomLayer.execManager.removeOutOfContexts(stableConsensus);
        }

        //define that end of this execution
        //stateManager.setWaiting(-1);
        tomLayer.setNoExec();

        decided.clear();

        Logger.println("(DeliveryThread.update) All finished from " + lastCheckpointEid + " to " + lastEid);
        //verify if there is a next proposal to be executed
        //(it only happens if the previous consensus were decided in a
        //round > 0
        /** Nao consigo perceber se isto tem utilidade neste contexto *****/
        //int nextExecution = lastEid + 1;
        //if(tomLayer.acceptor.executeAcceptedPendent(nextExecution)) {
        //Logger.println("(DeliveryThread.update) Executed propose for " + nextExecution);
        //}
        /******************************************************************/

        //canDeliver.signalAll();
        //deliverLock.unlock();
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
                
                //Consensus cons = decided.take(); // take a decided consensus
                /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
                Logger.println("(DeliveryThread.run) Waiting for a consensus to be delivered.");
                Consensus cons = decided.poll(1500, TimeUnit.MILLISECONDS); // take a decided consensus
                if (cons == null) {
                    Logger.println("(DeliveryThread.run) Timeout while waiting for a consensus, starting over.");
                    deliverUnlock();
                    continue;
                }
                Logger.println("(DeliveryThread.run) A consensus was delivered.");
                /******************************************************************/
                startTime = System.currentTimeMillis();

                //TODO: avoid the case in which the received valid proposal is
                //different from the decided value

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(cons.getDecision(), conf.getUseSignatures()==1);
                TOMMessage[] requests = (TOMMessage[]) cons.getDeserializedDecision();

                if (requests == null) {
                    Logger.println("(DeliveryThread.run) interpreting and verifying batched requests.");

                    requests = batchReader.deserialiseRequests();

                } else {
                    if(Logger.debug)
                        Logger.println("(DeliveryThread.run) using cached requests from the propose.");

                        }
                    tomLayer.clientsManager.getClientsLock().lock();
                    for (int i = 0; i < requests.length; i++) {

                        /** ISTO E CODIGO DO JOAO, PARA TRATAR DE DEBUGGING */
//                    if (Logger.debug)
//                        requests[i].setSequence(new DebugInfo(cons.getId(), cons.getDecisionRound(), lm.getLeader(cons.getId(), cons.getDecisionRound())));
                        /****************************************************/

                        requests[i].consensusStartTime = cons.startTime;
                        requests[i].consensusExecutionTime = cons.executionTime;
                        requests[i].consensusBatchSize = cons.batchSize;
                        tomLayer.clientsManager.requestOrdered(requests[i]);
                    }
                    tomLayer.clientsManager.getClientsLock().unlock();


                //set this consensus as the last executed
                tomLayer.setLastExec(cons.getId());

                //define the last stable consensus... the stable consensus can
                //be removed from the leaderManager and the executionManager
                /**/
                if (cons.getId() > 2) {
                    int stableConsensus = cons.getId() - 3;

                    tomLayer.lm.removeStableConsenusInfos(stableConsensus);
                    tomLayer.execManager.removeExecution(stableConsensus);
                }
                /**/
                //define that end of this execution
                tomLayer.setInExec(-1);

                //verify if there is a next proposal to be executed
                //(it only happens if the previous consensus were decided in a
                //round > 0
                int nextExecution = cons.getId() + 1;
                if(tomLayer.acceptor.executeAcceptedPendent(nextExecution)) {
                    Logger.println("(DeliveryThread.run) Executed propose for " + nextExecution);
                }

                

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {
                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;
                    receiver.receiveOrderedMessage(requests[i]);
                }

                /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */

                Logger.println("(DeliveryThread.run) I just delivered the batch of EID " + cons.getId());

                if (conf.isStateTransferEnabled()) {
                    Logger.println("(DeliveryThread.run) The state transfer protocol is enabled");
                    if (conf.getCheckpoint_period() > 0) {
                        if ((cons.getId() > 0) && ((cons.getId() % conf.getCheckpoint_period()) == 0)) {
                            Logger.println("(DeliveryThread.run) Performing checkpoint for consensus " + cons.getId());
                            byte[] state = receiver.getState();
                            tomLayer.saveState(state, cons.getId(), cons.getDecisionRound(), tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound()));
                            //TODO: possivelmente fazer mais alguma coisa
                        }
                        else {
                            Logger.println("(DeliveryThread.run) Storing message batch in the state log for consensus " + cons.getId());
                            tomLayer.saveBatch(cons.getDecision(), cons.getId(), cons.getDecisionRound(), tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound()));
                            //TODO: possivelmente fazer mais alguma coisa
                        }
                    }
                }
                /********************************************************/

                Logger.println("(DeliveryThread.run) All finished for " + cons.getId() + ", took " + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                e.printStackTrace(System.out);
            }
            /** ISTO E CODIGO DO JOAO, PARA TRATAR DA TRANSFERENCIA DE ESTADO */
            deliverUnlock();
            /******************************************************************/

        }
    }
}
