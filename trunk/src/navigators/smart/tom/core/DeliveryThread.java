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
import navigators.smart.paxosatwar.Consensus;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchReader;
import navigators.smart.tom.util.Logger;
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
    private ReconfigurationManager manager;

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param receiver Object that receives requests from clients
     * @param conf TOM configuration
     */
    public DeliveryThread(TOMLayer tomLayer, TOMRequestReceiver receiver, ReconfigurationManager manager) {
        super("Delivery Thread");

        this.tomLayer = tomLayer;
        this.receiver = receiver;
        //******* EDUARDO BEGIN **************//
        this.manager = manager;
        this.requestRecover = new RequestRecover(tomLayer, manager);
        //******* EDUARDO END **************//
    }

    /**
     * Invoked by the TOM layer, to deliver a decide consensus
     * @param cons Consensus established as being decided
     */
    public void delivery(Consensus cons) {
        try {
            //System.out.println("Consenso decidido! "+cons.getId());
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

                //System.out.println("(TESTE // DeliveryThread.update) EID: " + eid + ", round: " + state.getMessageBatch(eid).round + ", value: " + batch.length);

                tomLayer.lm.addLeaderInfo(eid, state.getMessageBatch(eid).round, state.getMessageBatch(eid).leader);

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(batch,
                        manager.getStaticConf().getUseSignatures() == 1);

                Logger.println("(DeliveryThread.update) interpreting and verifying batched requests.");

                TOMMessage[] requests = batchReader.deserialiseRequests(manager);

                tomLayer.clientsManager.getClientsLock().lock();
                for (int i = 0; i < requests.length; i++) {

                    tomLayer.clientsManager.requestOrdered(requests[i]);

                }

                //deliver the request to the application (receiver)
                tomLayer.clientsManager.getClientsLock().unlock();

                for (int i = 0; i < requests.length; i++) {

                    /******* Deixo isto comentado, pois nao me parece necessario      **********/
                    /******* Alem disso, esta informacao nao vem no TransferableState **********
                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;
                    /***************************************************************************/
                    
                    //receiver.receiveOrderedMessage(requests[i]);
                    
                    //******* EDUARDO BEGIN: Acho que precisa mudar aqui, como na entrega normal **************//
                     //TODO: verificar se aqui precisa mudar a enterga para as vistas
                     if (requests[i].getViewID() == this.manager.getCurrentViewId()) {
                        if (requests[i].getReqType() == ReconfigurationManager.TOM_NORMAL_REQUEST) {
                            receiver.receiveOrderedMessage(requests[i]);
                        } else {
                            //Reconfiguration request processing!
                            this.manager.enqueueUpdate(requests[i]);

                        }
                    } else {
                        this.tomLayer.getCommunication().send(new int[]{requests[i].getSender()},
                                new TOMMessage(this.manager.getStaticConf().getProcessId(),
                                requests[i].getSession(), requests[i].getSequence(),
                                TOMUtil.getBytes(this.manager.getCurrentView()), this.manager.getCurrentViewId()));
                    }
                    
                    //******* EDUARDO END: Acho que precisa mudar aqui, como na entrega normal **************//
                }

                // isto serve para retirar pedidos que nao cheguem a ser processados pela replica,
                // uma vez que se saltaram varias execucoes de consenso para a frente
                tomLayer.clientsManager.removeRequests(requests);

                //******* EDUARDO BEGIN: Acho que precisa mudar aqui, como na entrega normal **************//
                //TODO: verificar se aqui precisa mudar a enterga para as vistas
                if (this.manager.hasUpdates()) {
                    //System.out.println("Entrou aqui 1");
                    
                    //byte[] response = this.manager.executeUpdates(eid,state.getMessageBatch(eid).round,this.receiver.getState());
                    byte[] blabla = {1,2,3};
                    byte[] response = this.manager.executeUpdates(eid,state.getMessageBatch(eid).round,blabla);
                    TOMMessage[] dests = this.manager.clearUpdates();

                    for (int i = 0; i < dests.length; i++) {
                        //System.out.println("Entrou aqui 2");
                        this.tomLayer.getCommunication().send(new int[]{dests[i].getSender()},
                                new TOMMessage(this.manager.getStaticConf().getProcessId(), 
                                dests[i].getSession(), dests[i].getSequence(),
                                response, this.manager.getCurrentViewId()));

                    }
                    
                    this.tomLayer.getCommunication().updateServersConnections();
                    
                }
                //******* EDUARDO END: Acho que precisa mudar aqui, como na entrega normal **************//


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
                
                
                //System.out.println("vai entragar o consenso: "+cons.getId());

                //TODO: avoid the case in which the received valid proposal is
                //different from the decided value

                

                //System.out.println("chegou aqui 1: "+cons.getId());

                //System.out.println("(TESTE // DeliveryThread.run) EID: " + cons.getId() + ", round: " + cons.getDecisionRound() + ", value: " + cons.getDecision().length);

                TOMMessage[] requests = (TOMMessage[]) cons.getDeserializedDecision();

                 //System.out.println("chegou aqui 2: "+cons.getId());
                
                if (requests == null) {
                    
                     //System.out.println("chegou aqui 3 a: "+cons.getId());
                    
                    Logger.println("(DeliveryThread.run) interpreting and verifying batched requests.");

                    // obtain an array of requests from the taken consensus
                    BatchReader batchReader = new BatchReader(cons.getDecision(),
                            manager.getStaticConf().getUseSignatures() == 1);
                    requests = batchReader.deserialiseRequests(manager);

                } else {
                    //System.out.println("chegou aqui 3 b: "+cons.getId());
                    if (Logger.debug) {
                        Logger.println("(DeliveryThread.run) using cached requests from the propose.");
                    }

                }
                
               
                 //System.out.println("chegou aqui 4: "+cons.getId());
                
                tomLayer.clientsManager.getClientsLock().lock();
                 //System.out.println("chegou aqui 5: "+cons.getId());
                for (int i = 0; i < requests.length; i++) {

                    /** ISTO E CODIGO DO JOAO, PARA TRATAR DE DEBUGGING */
//                    if (Logger.debug)
//                        requests[i].setSequence(new DebugInfo(cons.getId(), cons.getDecisionRound(), lm.getLeader(cons.getId(), cons.getDecisionRound())));
                    /****************************************************/
                    requests[i].consensusStartTime = cons.startTime;
                    requests[i].consensusExecutionTime = cons.executionTime;
                    requests[i].consensusBatchSize = cons.batchSize;
                    //System.out.println("chegou aqui 6: "+cons.getId());
                    tomLayer.clientsManager.requestOrdered(requests[i]);
                   //System.out.println("chegou aqui 7: "+cons.getId()); 
                }
                tomLayer.clientsManager.getClientsLock().unlock();
                //System.out.println("chegou aqui 8: "+cons.getId());

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
               

                //verify if there is a next proposal to be executed
                //(it only happens if the previous consensus were decided in a
                //round > 0
                int nextExecution = cons.getId() + 1;
                if (tomLayer.acceptor.executeAcceptedPendent(nextExecution)) {
                    Logger.println("(DeliveryThread.run) Executed propose for " + nextExecution);
                }

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {
                    requests[i].requestTotalLatency = System.currentTimeMillis() - cons.startTime;

                    //******* EDUARDO BEGIN **************//
                    if (requests[i].getViewID() == this.manager.getCurrentViewId()) {
                        if (requests[i].getReqType() == ReconfigurationManager.TOM_NORMAL_REQUEST) {
                            receiver.receiveOrderedMessage(requests[i]);
                        } else {
                            //Reconfiguration request processing!
                            this.manager.enqueueUpdate(requests[i]);

                        }
                    } else {
                        this.tomLayer.getCommunication().send(new int[]{requests[i].getSender()},
                                new TOMMessage(this.manager.getStaticConf().getProcessId(),
                                requests[i].getSession(), requests[i].getSequence(),
                                TOMUtil.getBytes(this.manager.getCurrentView()), this.manager.getCurrentViewId()));
                    }
                    
                        //******* EDUARDO END **************//
                }


                //******* EDUARDO BEGIN **************//
                if (this.manager.hasUpdates()) {
                    //System.out.println("Entrou aqui 1");
                    
                    
                    
                    //byte[] response = this.manager.executeUpdates(cons.getId(),cons.getDecisionRound().getNumber(),this.receiver.getState());
                    byte[] blabla = {1,2,3};
                    byte[] response = this.manager.executeUpdates(cons.getId(), cons.getDecisionRound().getNumber(),blabla);
                    TOMMessage[] dests = this.manager.clearUpdates();

                    for (int i = 0; i < dests.length; i++) {
                        //System.out.println("Entrou aqui 2");
                        this.tomLayer.getCommunication().send(new int[]{dests[i].getSender()},
                                new TOMMessage(this.manager.getStaticConf().getProcessId(), dests[i].getSession(),
                                dests[i].getSequence(), response, this.manager.getCurrentViewId()));

                    }
                    
                    this.tomLayer.getCommunication().updateServersConnections();
                    
                }
                //******* EDUARDO END **************//
                
                 //define that end of this execution
                tomLayer.setInExec(-1);
                tomLayer.processOutOfContext();

                /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
                Logger.println("(DeliveryThread.run) I just delivered the batch of EID " + cons.getId());

                if (manager.getStaticConf().isStateTransferEnabled()) {
                    Logger.println("(DeliveryThread.run) The state transfer protocol is enabled");
                    if (manager.getStaticConf().getCheckpoint_period() > 0) {
                        if ((cons.getId() > 0) && ((cons.getId() % manager.getStaticConf().getCheckpoint_period()) == 0)) {
                            Logger.println("(DeliveryThread.run) Performing checkpoint for consensus " + cons.getId());
                            byte[] state = receiver.getState();
                            tomLayer.saveState(state, cons.getId(), cons.getDecisionRound().getNumber(), tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber()));
                        //TODO: possivelmente fazer mais alguma coisa
                        } else {
                            Logger.println("(DeliveryThread.run) Storing message batch in the state log for consensus " + cons.getId());
                            tomLayer.saveBatch(cons.getDecision(), cons.getId(), cons.getDecisionRound().getNumber(), tomLayer.lm.getLeader(cons.getId(), cons.getDecisionRound().getNumber()));
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
