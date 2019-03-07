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
    private byte[] tempMsg = null;
    private byte[] tempSig = null;
    
    private ReentrantLock clientsLock = new ReentrantLock();

    public ClientsManager(ServerViewController controller, RequestsTimer timer, RequestVerifier verifier) {
        this.controller = controller;
        this.timer = timer;
        this.verifier = verifier;
        
        if (controller.getStaticConf().getUseSignatures() == 2) {
            tempMsg = new byte []{3,5,6,7,4,3,5,6,4,7,4,1,7,7,5,4,3,1,4,85,7,5,7,3};
            tempSig = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), tempMsg);            
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
                //just account for the message reception
                clientData.setLastMessageReceived(request.getSequence());
                clientData.setLastMessageReceivedTime(request.receptionTime);

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

                if (tempMsg != null && tempSig != null)
                    TOMUtil.verifySignature(controller.getStaticConf().getPublicKey(), tempMsg, tempSig);
                
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
}
