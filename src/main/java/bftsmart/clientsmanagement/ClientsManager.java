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
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.leaderchange.RequestsTimer;
import bftsmart.tom.server.RequestVerifier;
import bftsmart.tom.util.TOMUtil;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author alysson
 */
public class ClientsManager {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private ServerViewController controller;
    private RequestsTimer timer;
    private HashMap<Integer, ClientData> clientsData = new HashMap<Integer, ClientData>();
    private RequestVerifier verifier;
    
    //Used when the intention is to perform benchmarking with signature verification, but
    //without having to make the clients create one first. Useful to optimize resources
    private byte[] benchMsg = null;
    private byte[] benchSig = null;
    private HashMap<String,Signature> benchEngines = new HashMap<>();
    
    private ReentrantLock clientsLock = new ReentrantLock();

    public ClientsManager(ServerViewController controller, RequestsTimer timer, RequestVerifier verifier) {
        this.controller = controller;
        this.timer = timer;
        this.verifier = verifier;
        
        if (controller.getStaticConf().getUseSignatures() == 2) {
            benchMsg = new byte []{3,5,6,7,4,3,5,6,4,7,4,1,7,7,5,4,3,1,4,85,7,5,7,3};
            benchSig = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), benchMsg);            
        }
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
        long allReqSizeInBytes = 0;
        boolean allReqSizeInBytesExceeded = false;
        
        clientsLock.lock();
        /******* BEGIN CLIENTS CRITICAL SECTION ******/
        
        List<Entry<Integer, ClientData>> clientsEntryList = new ArrayList<>(clientsData.entrySet().size());
        clientsEntryList.addAll(clientsData.entrySet());
        
        if (controller.getStaticConf().getFairBatch()) // ensure fairness
            Collections.shuffle(clientsEntryList);

        logger.debug("Number of active clients: {}", clientsEntryList.size());
        
        for (int i = 0; true; i++) {
                        
            Iterator<Entry<Integer, ClientData>> it = clientsEntryList.iterator();
            int noMoreMessages = 0;
            
            logger.debug("Fetching requests with internal index {}", i);
            
            while (it.hasNext()
                    && allReq.size() < controller.getStaticConf().getMaxBatchSize()
                    && noMoreMessages < clientsEntryList.size()) {

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
                        if(allReqSizeInBytes + request.serializedMessage.length <= controller.getStaticConf().getMaxBatchSizeInBytes()) {

                            logger.debug("Selected request with sequence number {} from client {}", request.getSequence(), request.getSender());

                            //this client have pending message
                            request.alreadyProposed = true;
                            allReq.addLast(request);
                            allReqSizeInBytes += request.serializedMessage.length;
                        } else {
                            allReqSizeInBytesExceeded = true;
                        }
                    }
                } else {
                    //this client don't have more pending requests
                    noMoreMessages++;
                }
            }
            
            if(allReq.size() == controller.getStaticConf().getMaxBatchSize() ||
                    noMoreMessages == clientsEntryList.size() ||
                    allReqSizeInBytesExceeded) {
                
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
     * Retrieves the number of pending requests and their sizes
     * and checks if there are enough to fill the next batch completely.
     * @return true if there are enough requests and false otherwise
     */
    public boolean isNextBatchReady() {
        int count = 0;
        long size = 0;

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
                        size += msg.serializedMessage.length;
                    }
                }
            }
            clientData.clientLock.unlock();
        }

        /******* END CLIENTS CRITICAL SECTION ******/
        clientsLock.unlock();
        return count >= controller.getStaticConf().getMaxBatchSize()
                || size >= controller.getStaticConf().getMaxBatchSizeInBytes();
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

    public boolean requestReceived(TOMMessage request, boolean fromClient) {
        return requestReceived(request, fromClient, null);
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
    public boolean requestReceived(TOMMessage request, boolean fromClient, ServerCommunicationSystem cs) {
                
        long receptionTime = System.nanoTime();
        long receptionTimestamp = System.currentTimeMillis();
        
        int clientId = request.getSender();
        boolean accounted = false;

        ClientData clientData = getClientData(clientId);
        
        if(request.getSequence() < 0) {
            //Do not accept this faulty message. -1 is the initial value which will bypass the sequence-checking further down in the function
            return false;
        }

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
        
        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        //Logger.println("(ClientsManager.requestReceived) lock for client "+clientData.getClientId()+" acquired");

        /* ################################################ */
        //pjsousa: simple flow control mechanism to avoid out of memory exception
        if (fromClient && (controller.getStaticConf().getUseControlFlow() != 0)) {
            if (clientData.getPendingRequests().size() > controller.getStaticConf().getUseControlFlow()) {
                //clients should not have more than defined in the config file
                //outstanding messages, otherwise they will be dropped.
                //just account for the message reception //why is this necessary?
                //Scenario: A faulty client sends its request to the leader first which will discard the message due to a full queue.
                //          Then the client sends the message delayed (queue is now not full) to the followers, which will forward the message to the leader after the timeout.
                //          The leader then would reject the message, because it already accounted for the message reception. -> leader change
                //clientData.setLastMessageReceived(request.getSequence());
                //clientData.setLastMessageReceivedTime(request.receptionTime);

                clientData.clientLock.unlock();
                return false;
            }
        }
        /* ################################################ */

        //new session... just reset the client counter
        if (clientData.getSession() != request.getSession()) {
            clientData.setSession(request.getSession());
            clientData.setLastMessageReceived(-1);
            clientData.setLastMessageDelivered(-1);
            clientData.getOrderedRequests().clear();
            clearPendingRequests(clientData);
        }

        if ((clientData.getLastMessageReceived() == -1) || //first message received or new session (see above)
                (clientData.getLastMessageReceived() + 1 == request.getSequence()) || //message received is the expected
                ((request.getSequence() > clientData.getLastMessageReceived()) && !fromClient)) {

            //enforce the "external validity" property, i.e, verify if the
            //requests are valid in accordance to the application semantics
            //and not an erroneous requests sent by a Byzantine leader.
            boolean isValid = (!controller.getStaticConf().isBFT() || verifier.isValidRequest(request));

            Signature engine = benchEngines.get(Thread.currentThread().getName());
            
            if (engine == null) {
                
                try {
                    engine = TOMUtil.getSigEngine();
                    engine.initVerify(controller.getStaticConf().getPublicKey());
                    
                    benchEngines.put(Thread.currentThread().getName(), engine);
                } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
                    logger.error("Signature error.",ex);
                    engine = null;
                }
            }
            
            //it is a valid new message and I have to verify it's signature
            if (isValid &&
                    ((engine != null && benchMsg != null && benchSig != null && TOMUtil.verifySigForBenchmark(engine, benchMsg, benchSig)) 
                            || (((!request.signed) || clientData.verifySignature(request.serializedMessage, request.serializedMessageSignature)) // message is either not signed or if it is signed the signature is valid
                                    && (controller.getStaticConf().getUseSignatures() != 1 || request.signed || !fromClient)))) { // additionally, unsigned messages from the client are not allowed when useSignatures == 1. Forwarded and proposed requests do not have 'signed' set to true.

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
                
                if (reply != null && cs != null) {
                    
                    if (reply.recvFromClient && fromClient) {
                        logger.info("[CACHE] re-send reply [Sender: " + reply.getSender() + ", sequence: " + reply.getSequence()+", session: " + reply.getSession()+ "]");
                        cs.send(new int[]{request.getSender()}, reply);

                    } 
                    
                    else if (!reply.recvFromClient && fromClient) {
                        reply.recvFromClient = true;
                    }
                    
                }
                accounted = true;
            } else {
                
                logger.warn("Message from client {} is too forward", clientData.getClientId());
                
                //a too forward message... the client must be malicious
                accounted = false;
            }
        }

        /******* END CLIENTDATA CRITICAL SECTION ******/
        
        clientData.clientLock.unlock();

        return accounted;
    }

    /**
     * Caller must call lock() and unlock() on clientData.clientLock
     * @param clientData the clientData associated with the client
     */
	private void clearPendingRequests(ClientData clientData) {
        for(TOMMessage m : clientData.getPendingRequests()) {
            if(timer != null) {
                //Clear all pending timers before clearing the requests. (For synchronous closed-loop clients there are never pending requests.)
                //Without clearing the timer a leader change would be triggered, because the removed request will never be processed.
                timer.unwatch(m);
	        }
	    }
        clientData.getPendingRequests().clear();
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
        ClientData clientData = getClientData(request.getSender());
        clientData.clientLock.lock();

        //stops the timer associated with this message
        if (timer != null) {
            timer.unwatch(request);
        }

        /******* BEGIN CLIENTDATA CRITICAL SECTION ******/
        if (!clientData.removeOrderedRequest(request)) {
            logger.debug("Request " + request + " does not exist in pending requests");
        }
        if(clientData.getSession() == request.getSession()) {
            //When a client sends a message with a big sequence number and shortly afterwards a message with
            //a new session and a lower sequence number, do not set the last delivered sequence number to the big number,
            //which would cause all following messages of the new sequence to be invalid.
            //-> only set the number if it is the same session
            clientData.setLastMessageDelivered(request.getSequence());
        }

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
}
