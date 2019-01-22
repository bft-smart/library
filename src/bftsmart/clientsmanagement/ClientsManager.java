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
package bftsmart.clientsmanagement;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.MessageContext;
import bftsmart.tom.core.DeliveryThread;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.leaderchange.RequestsTimer;
import bftsmart.tom.server.RequestVerifier;
import bftsmart.tom.util.TOMUtil;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author alysson
 */
public class ClientsManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private boolean doWork = true;
    private ServerViewController controller;
    private RequestsTimer timer;
    private HashMap<Integer, ClientData> clientsData = new HashMap<Integer, ClientData>();
    private RequestVerifier verifier;
    private boolean ignore = false;
    private ExecutionManager manager;
    private DeliveryThread dt;
    private ServerCommunicationSystem cs;
    
    private ReentrantLock clientsLock = new ReentrantLock();
    
    private LinkedBlockingQueue<TOMMessage> ackQueue;
    private Thread ackReplier;

    public ClientsManager(ServerViewController controller, RequestsTimer timer, RequestVerifier verifier, 
            ServerCommunicationSystem cs, ExecutionManager manager, DeliveryThread dt) {
        
        this.controller = controller;
        this.timer = timer;
        this.verifier = verifier;
        this.cs = cs;
        this.manager = manager;
        this.dt = dt;
        
        ackQueue = new LinkedBlockingQueue<>();
        ackReplier = new Thread() {
            
            public void run() {
                
                while (doWork) {
                    
                    try {
                        TOMMessage request = null;
                        request = ackQueue.poll(100,TimeUnit.MILLISECONDS);
                        if (request != null)
                            cs.send(new int[]{request.getSender()}, request.reply);
                    } catch (InterruptedException ex) {
                        logger.error("Interrupted exception", ex);
                    }
                }
            }
        };
        
        ackReplier.start();
    }

    /**
     * We are assuming that no more than one thread will access
     * the same clientData during creation.
     *
     *
     * @param clientId
     * @return the ClientData stored on the manager
     */
    public ClientData getClientData(int clientId) {
        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/
        ClientData clientData = clientsData.get(clientId);

        if (clientData == null) {
            logger.debug("Creating new client data, client id=" + clientId);

            //******* EDUARDO BEGIN **************//
            clientData = new ClientData(clientId,
                    (controller.getStaticConf().getUseSignatures() == 1)
                    ? controller.getStaticConf().getPublicKey(clientId)
                    : null);
            //******* EDUARDO END **************//
            clientsData.put(clientId, clientData);
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();

        return clientData;
    }

    /**
     * Get pending requests in a fair way (one request from each client
     * queue until the max number of requests is obtained).
     *
     * @return the set of all pending requests of this system
     */
    public RequestList getPendingRequests() {
        RequestList allReq = new RequestList();
        
        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/
        
        Set<Entry<Integer, ClientData>> clientsEntrySet = clientsData.entrySet();
        logger.debug("Number of active clients: {}", clientsEntrySet.size());
        
        for (int i = 0; true; i++) {
            Iterator<Entry<Integer, ClientData>> it = clientsEntrySet.iterator();
            int noMoreMessages = 0;
            
            logger.debug("Fetching requests with internal index {}", i);

            while (it.hasNext()
                    && allReq.size() < controller.getStaticConf().getMaxBatchSize()
                    && noMoreMessages < clientsEntrySet.size()) {

                ClientData clientData = it.next().getValue();
                RequestList clientPendingRequests = clientData.getPendingRequests();

                clientData.clientLock.lock();

                logger.debug("Number of pending requests for client {}: {}.", clientData.getClientId(), clientPendingRequests.size());

                /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
                TOMMessage request = (clientPendingRequests.size() > i) ? clientPendingRequests.get(i) : null;

                /******* END CLIENTDATA CRITICAL SECTION ******/
                clientData.clientLock.unlock();

                if (request != null) {
                    if(!request.alreadyProposed) {
                        
                        logger.debug("Selected request with sequence number {} from client {}", request.getSequence(), request.getSender());
                        
                        //this client have pending message
                        request.alreadyProposed = true;
                        allReq.addLast(request);
                    }
                } else {
                    //this client don't have more pending requests
                    noMoreMessages++;
                }
            }
            
            if(allReq.size() == controller.getStaticConf().getMaxBatchSize() ||
                    noMoreMessages == clientsEntrySet.size()) {
                
                break;
            }
        }
        
        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return allReq;
    }

    /**
     * We've implemented some protection for individual client
     * data, but the clients table can change during the operation.
     *
     * @return true if there are some pending requests and false otherwise
     */
    public boolean havePendingRequests() {
        boolean havePending = false;

        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/        
        
        Iterator<Entry<Integer, ClientData>> it = clientsData.entrySet().iterator();

        while (it.hasNext() && !havePending) {
            ClientData clientData = it.next().getValue();
            
            clientData.clientLock.lock();
            RequestList reqs = clientData.getPendingRequests();
            if (!reqs.isEmpty()) {
                for(TOMMessage msg:reqs) {
                    if(!msg.alreadyProposed) {
                        havePending = true;
                        break;
                    }
                }
            }
            clientData.clientLock.unlock();
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return havePending;
    }
    
    /**
     * Retrieves the number of pending requests
     * @return Number of pending requests
     */
    public int countPendingRequests() {
        int count = 0;

        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/        
        
        Iterator<Entry<Integer, ClientData>> it = clientsData.entrySet().iterator();

        while (it.hasNext()) {
            ClientData clientData = it.next().getValue();
            
            clientData.clientLock.lock();
            RequestList reqs = clientData.getPendingRequests();
            if (!reqs.isEmpty()) {
                for(TOMMessage msg:reqs) {
                    if(!msg.alreadyProposed) {
                        count++;
                    }
                }
            }
            clientData.clientLock.unlock();
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return count;
    }

    /**
     * Verifies if some reqId is pending.
     *
     * @param reqId the request identifier
     * @return true if the request is pending
     */
    public boolean isPending(int reqId) {
        return getPending(reqId) != null;
    }

    /**
     * Get some reqId that is pending.
     *
     * @param reqId the request identifier
     * @return the pending request, or null
     */
    public TOMMessage getPending(int reqId) {
        ClientData clientData = getClientData(TOMMessage.getSenderFromId(reqId));

        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        TOMMessage pendingMessage = clientData.getPendingRequests().getById(reqId);

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();

        return pendingMessage;
    }

    /**
     * Notifies the ClientsManager that a new request from a client arrived.
     * This method updates the ClientData of the client request.getSender().
     *
     * @param request the received request
     * @param fromClient the message was received from client or not?
     * @param storeMessage the message should be stored or not? (read-only requests are not stored)
     * @param cs server com. system to be able to send replies to already processed requests
     *
     * @return true if the request is ok and is added to the pending messages
     * for this client, false if there is some problem and the message was not
     * accounted
     */
    public boolean requestReceived(TOMMessage request, boolean fromClient) {
                
        int pendingReqs = countPendingRequests();
        int pendingDecs = dt.getPendingDecisions();
        int pendingReps = dt.getReplyManager().getPendingReplies();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                
        //control flow mechanism
        if (fromClient && this.controller.getStaticConf().getControlFlow()) {

                if ((this.controller.getStaticConf().getMaxPendigReqs() > 0 && pendingReqs >= this.controller.getStaticConf().getMaxPendigReqs()) || 
                        (this.controller.getStaticConf().getMaxPendigDecs() > 0 && pendingDecs >= this.controller.getStaticConf().getMaxPendigDecs()) ||
                        (this.controller.getStaticConf().getMaxPendigReps() > 0 && pendingDecs >= this.controller.getStaticConf().getMaxPendigReps()) ||
                        (this.controller.getStaticConf().getMaxUsedMemory() > 0 && usedMemory >= this.controller.getStaticConf().getMaxUsedMemory()))
                                {

                    ignore = true;

                } else if ((this.controller.getStaticConf().getMaxPendigReqs() < 0 || pendingReqs <= this.controller.getStaticConf().getPreferredPendigReqs()) && 
                        (this.controller.getStaticConf().getMaxPendigDecs() < 0 || pendingDecs <= this.controller.getStaticConf().getPreferredPendigDecs()) &&
                        (this.controller.getStaticConf().getMaxPendigReps() < 0 || pendingReps <= this.controller.getStaticConf().getPreferredPendigReps()) &&
                        (this.controller.getStaticConf().getMaxUsedMemory() < 0 || usedMemory <= this.controller.getStaticConf().getPreferredUsedMemory()))
                        {

                    ignore = false;
                }

            if (ignore) {
                
                if(this.controller.getStaticConf().getMaxUsedMemory() > 0 &&
                        usedMemory > this.controller.getStaticConf().getPreferredUsedMemory()) Runtime.getRuntime().gc(); // force garbage collection

                logger.warn("Discarding message due to control flow mechanism\n" +
                        "\tMaximum requests are {}, pending requests at {}\n" + 
                        "\tMaximum decisions are {}, pending decisions at {}\n" +
                        "\tMaximum replies are {}, pending replies at {}\n" + 
                        "\tMaximum memory is {} current memory at {}\n",
                        this.controller.getStaticConf().getMaxPendigReqs(), pendingReqs,
                        this.controller.getStaticConf().getMaxPendigDecs(), pendingDecs,
                        this.controller.getStaticConf().getMaxPendigReps(), pendingReps,
                        TOMUtil.humanReadableByteCount(this.controller.getStaticConf().getMaxUsedMemory(), false),
                        TOMUtil.humanReadableByteCount(usedMemory, false));

                return false;
            }
        }
        
        long receptionTime = System.nanoTime();
        long receptionTimestamp = System.currentTimeMillis();
        
        int clientId = request.getSender();
        boolean accounted = false;

        ClientData clientData = getClientData(clientId);
        
        clientData.clientLock.lock();
        
        //Is this a leader replay attack?
        if (!fromClient && clientData.getSession() == request.getSession() &&
                clientData.getLastMessageDelivered() >= request.getSequence()) {
            
            clientData.clientLock.unlock();
            logger.warn("Detected a leader replay attack, rejecting request");
            return false;
        }

        request.receptionTime = receptionTime;
        request.receptionTimestamp = receptionTimestamp;
        
        //new session... just reset the client counter
        if (clientData.getSession() != request.getSession()) {
            clientData.setSession(request.getSession());
            clientData.setLastMessageReceived(-1);
            clientData.setLastMessageDelivered(-1);
            clientData.getOrderedRequests().clear();
            clientData.getPendingRequests().clear();
        }

        if ((clientData.getLastMessageReceived() == -1) || //first message received or new session (see above)
                (clientData.getLastMessageReceived() + 1 == request.getSequence()) || //message received is the expected
                ((request.getSequence() > clientData.getLastMessageReceived()) && !fromClient)) {

            //enforce the "external validity" property, i.e, verify if the
            //requests are valid in accordance to the application semantics
            //and not an erroneous requests sent by a Byzantine leader.
            boolean isValid = (!controller.getStaticConf().isBFT() || verifier.isValidRequest(request));

            //it is a valid new message and I have to verify it's signature
            if (isValid &&
                    (!request.signed ||
                    clientData.verifySignature(request.serializedMessage,
                            request.serializedMessageSignature))) {
                
                logger.debug("Message from client {} is valid", clientData.getClientId());

                //I don't have the message but it is valid, I will
                //insert it in the pending requests of this client

                request.recvFromClient = fromClient;
                clientData.getPendingRequests().add(request); 
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);

                //create a timer for this message
                if (timer != null) {
                    timer.watch(request);
                }

                sendAck(fromClient, request);
                
                accounted = true;
            } else {
                
                logger.warn("Message from client {} is invalid", clientData.getClientId());
            }
        } else {
            //I will not put this message on the pending requests list
            if (clientData.getLastMessageReceived() >= request.getSequence()) {
                //I already have/had this message
                
                //send reply if it is available
                TOMMessage reply = clientData.getReply(request.getSequence());
                MessageContext ctx = clientData.getContext(request.getSequence());
                TOMMessage clone = null;
                
                try {
                    clone = (TOMMessage) request.clone();
                } catch (CloneNotSupportedException ex) {
                    logger.error("Error cloning object.",ex);
                }
                
                if (reply != null && ctx != null && clone != null) {
                    
                    if (reply.recvFromClient && fromClient) {
                        logger.info("[CACHE] re-send reply [Sender: " + request.getSender() + ", sequence: " + reply.getSequence()+", session: " + reply.getSession()+ "]");
                                                
                        clone.reply = reply;
                        clone.msgCtx = ctx;

                        dt.getReplyManager().send(clone);
                        
                        //cs.send(new int[]{request.getSender()}, reply);

                    } 
                    
                    else if (!reply.recvFromClient && fromClient) {
                        reply.recvFromClient = true;
                    }
                    
                } else {
                    sendAck(fromClient, request);
                }
                accounted = true;
            } else {
                
                logger.warn("Message from client {} is too forward  (last message received was #{} and last delivered was #{})",
                        clientData.getClientId(), clientData.getLastMessageReceived(), clientData.getLastMessageDelivered());
                
                //a too forward message... the client must be malicious
                accounted = false;
            }
        }

        /******* END CLIENTDATA CRITICAL SECTION ******/
        
        clientData.clientLock.unlock();

        return accounted;
    }

    public void sendAck(boolean fromClient, TOMMessage request) {
        if ((fromClient || !request.ackSent) && this.controller.getStaticConf().getControlFlow() && cs != null) {
            
            logger.debug("Sending ACK to client {}", request.getSender());
            
            ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * 2);
            
            buff.putInt(request.getAckSeq());
            buff.putInt(manager.getCurrentLeader());
                    
            TOMMessage ack = new TOMMessage(controller.getStaticConf().getProcessId(), request.getSession(), request.getSequence(), 
                    request.getOperationId(), buff.array(), request.getViewID(), TOMMessageType.ACK);
            
            try {
                
                TOMMessage clone = (TOMMessage) request.clone();
            
                clone.reply = ack;
            
                ackQueue.put(clone);
                
            } catch (InterruptedException | CloneNotSupportedException ex) {
                logger.error("Error while sending ACK", ex);
            }
                                    
            request.ackSent = true;
        }
    }
    /**
     * Notifies the ClientsManager that these requests were already executed.
     * 
     * @param requests the array of requests to account as ordered
     */
    public void requestsOrdered(TOMMessage[] requests) {
        clientsLock.lock();
        logger.debug("Updating client manager");
        for (TOMMessage request : requests) {
            requestOrdered(request);
        }
        logger.debug("Finished updating client manager");
        clientsLock.unlock();
    }

    /**
     * Cleans all state for this request (e.g., removes it from the pending
     * requests queue and stop any timer for it).
     *
     * @param request the request ordered by the consensus
     */
    private void requestOrdered(TOMMessage request) {
        //stops the timer associated with this message
        if (timer != null) {
            timer.unwatch(request);
        }

        ClientData clientData = getClientData(request.getSender());

        clientData.clientLock.lock();
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        if (!clientData.removeOrderedRequest(request)) {
            logger.debug("Request " + request + " does not exist in pending requests");
        }
        clientData.setLastMessageDelivered(request.getSequence());

        /******* END CLIENTDATA CRITICAL SECTION ******/
        clientData.clientLock.unlock();
    }

    public ReentrantLock getClientsLock() {
        return clientsLock;
    }
    
    public void clear() {
        clientsLock.lock();
        clientsData.clear();
        clientsLock.unlock();
        logger.info("ClientsManager cleared.");

    }
    
    public int numClients() {
        
        return clientsData.size();
    }
    
    public void shutdown() {
        
        clear();
        getPendingRequests().clear();
        doWork = false;
    }
    
}
