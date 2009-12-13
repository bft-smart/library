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

import navigators.smart.paxosatwar.Consensus;
import navigators.smart.tom.TOMRequestReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchReader;
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

    /**
     * This is the code for the thread. It delivers decided consensus to the TOM request receiver object (which is the application)
     */
    @Override
    public void run() {
        long startTime;
        while (true) {
            try {
                Consensus cons = decided.take(); // take a decided consensus
                startTime = System.currentTimeMillis();

                //TODO: avoid the case in which the received valid proposal is
                //different from the decided value

                // obtain an array of requests from the taken consensus
                BatchReader batchReader = new BatchReader(cons.getDecision(), conf.getUseSignatures()==1);
                TOMMessage[] requests = (TOMMessage[]) cons.getDeserializedDecision();

                if (requests == null) {
                    Logger.println("(DeliveryThread.run) interpreting and verifying batched requests.");
                    int numberOfMessages = batchReader.getNumberOfMessages();

                    requests = new TOMMessage[numberOfMessages];

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

                        try {
                            DataInputStream ois = new DataInputStream(new ByteArrayInputStream(message));
                            TOMMessage tm = new TOMMessage();
                            tm.readExternal(ois);
                            tm.consensusStartTime = cons.startTime;
                            tm.consensusExecutionTime = cons.executionTime;
                            tm.consensusBatchSize = cons.batchSize;
                            requests[i] = tm;
                            //requests[i] = (TOMMessage) ois.readObject();
                            tomLayer.clientsManager.requestOrdered(requests[i]);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    Logger.println("(DeliveryThread.run) using cached requests from the propose.");
                    tomLayer.clientsManager.getClientsLock().lock();
                    for (int i = 0; i < requests.length; i++) {
                        requests[i].consensusStartTime = cons.startTime;
                        requests[i].consensusExecutionTime = cons.executionTime;
                        requests[i].consensusBatchSize = cons.batchSize;
                        tomLayer.clientsManager.requestOrdered(requests[i]);
                    }
                    tomLayer.clientsManager.getClientsLock().unlock();

                    batchReader.skipMessages();
                }

                //set this consensus as the last executed
                tomLayer.setLastExec(cons.getId());

                //define the last stable consensus... the stable consensus can
                //be removed from the leaderManager and the executionManager
                if (cons.getId() > 2) {
                    int stableConsensus = cons.getId() - 3;

                    tomLayer.lm.removeStableConsenusInfos(stableConsensus);
                    tomLayer.execManager.removeExecution(stableConsensus);
                }

                //define that end of this execution
                tomLayer.setInExec(-1);

                //verify if there is a next proposal to be executed
                //(it only happens if the previous consensus were decided in a
                //round > 0
                int nextExecution = cons.getId() + 1;
                if(tomLayer.acceptor.executeAcceptedPendent(nextExecution)) {
                    Logger.println("(DeliveryThread.run) Executed propose for " + nextExecution);
                }

                //obtain the nonce and timestamps to be delivered to the application
                long timestamp = batchReader.getTimestamp();
                byte[] nonces = new byte[batchReader.getNumberOfNonces()];
                if (nonces.length > 0) {
                    batchReader.getNonces(nonces);
                }

                //deliver the request to the application (receiver)
                for (int i = 0; i < requests.length; i++) {
                    requests[i].timestamp = timestamp;
                    requests[i].nonces = nonces;
                    requests[i].requestTotalLatency = System.currentTimeMillis()-cons.startTime;
                    receiver.receiveOrderedMessage(requests[i]);
                }

                /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
                if (conf.getCheckpoint_period() > 0) {
                    if ((cons.getId() > 0) && (cons.getId() % conf.getCheckpoint_period() == 0)) {
                        Logger.println("(DeliveryThread.run) Performing checkpoint for consensus " + cons.getId());
                        byte[] state = receiver.getState();
                        tomLayer.saveState(state);
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                    else {
                        Logger.println("(DeliveryThread.run) Storing message batch in the state log for consensus " + cons.getId());
                        tomLayer.saveBatch(cons.getDecision());
                        //TODO: possivelmente fazer mais alguma coisa
                    }
                }
                /********************************************************/

                Logger.println("(DeliveryThread.run) All finished for " + cons.getId() + ", took " + (System.currentTimeMillis() - startTime));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
