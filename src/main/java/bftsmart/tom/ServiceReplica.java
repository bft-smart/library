/**
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and
 * the authors indicated in the @author tags
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package bftsmart.tom;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.consensus.roles.Acceptor;
import bftsmart.consensus.roles.Proposer;
import bftsmart.reconfiguration.IReconfigurationListener;
import bftsmart.reconfiguration.ReconfigureReply;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.VMMessage;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.core.ReplyManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.leaderchange.CertifiedDecision;
import bftsmart.tom.server.*;
import bftsmart.tom.server.defaultservices.DefaultReplier;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.ServiceContent;
import bftsmart.tom.util.ShutdownHookThread;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class receives messages from DeliveryThread and manages the execution
 * from the application and reply to the clients. For applications where the
 * ordered messages are executed one by one, ServiceReplica receives the batch
 * decided in a consensus, deliver one by one and reply with the batch of
 * replies. In cases where the application executes the messages in batches, the
 * batch of messages is delivered to the application and ServiceReplica doesn't
 * need to organize the replies in batches.
 */
public class ServiceReplica implements IResponseSender {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
	private final Logger measurementLogger = LoggerFactory.getLogger("measurement");

    // replica ID
    private final int id;
    // Server side communication system
    private ServerCommunicationSystem cs;
    private ReplyManager repMan;
    private final ServerViewController SVController;
    private final ReentrantLock waitTTPJoinMsgLock = new ReentrantLock();
    private final Condition canProceed = waitTTPJoinMsgLock.newCondition();
    private final Executable executor;
    private final Recoverable recoverer;
    private TOMLayer tomLayer;
    private boolean tomStackCreated;
    private ReplicaContext replicaCtx;
    private final Replier replier;
    private final RequestVerifier verifier;
	private final ProposeRequestVerifier proposeRequestVerifier;
	private final IReconfigurationListener reconfigurationListener;

    /**
     * Constructor
     *
     * @param id Replica ID
     * @param executor Executor
     * @param recoverer Recoverer
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer) {
        this(id, "", executor, recoverer, null, new DefaultReplier(), null, null, null);
    }

	public ServiceReplica(int id, Executable executor, Recoverable recoverer,
						  ProposeRequestVerifier proposeRequestVerifier) {
		this(id, "", executor, recoverer, null, new DefaultReplier(), null,
				proposeRequestVerifier, null);
	}

	public ServiceReplica(int id, Executable executor, Recoverable recoverer,
						  ProposeRequestVerifier proposeRequestVerifier,
						  IReconfigurationListener reconfigurationListener) {
		this(id, "", executor, recoverer, null, new DefaultReplier(), null,
				proposeRequestVerifier, reconfigurationListener);
	}

    /**
     * Constructor
     *
     * @param id Replica ID
     * @param executor Executor
     * @param recoverer Recoverer
     * @param verifier Requests verifier
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer, RequestVerifier verifier) {
        this(id, "", executor, recoverer, verifier, new DefaultReplier(), null, null, null);
    }
    
    /**
     * Constructor
     *
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer, RequestVerifier verifier,
						  Replier replier) {
        this(id, "", executor, recoverer, verifier, replier, null, null, null);
    }
    
    /**
     * Constructor
     *
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer, RequestVerifier verifier,
						  Replier replier, KeyLoader loader) {
        this(id, "", executor, recoverer, verifier, replier, loader, null, null);
    }
    /**
     * Constructor
     *
     * @param id Replica ID
     * @param configHome Configuration directory for BFT-SMART
     * @param executor The executor implementation
     * @param recoverer The recoverer implementation
     * @param verifier Requests Verifier
     * @param replier Can be used to override the targets of the replies associated to each request.
     * @param loader Used to load signature keys from disk
	 * @param proposeRequestVerifier Verifier for propose requests
	 * @param reconfigurationListener Listener for reconfiguration events
     */
    public ServiceReplica(int id, String configHome, Executable executor, Recoverable recoverer,
						  RequestVerifier verifier, Replier replier, KeyLoader loader,
						  ProposeRequestVerifier proposeRequestVerifier,
						  IReconfigurationListener reconfigurationListener) {
        this.id = id;
        this.SVController = new ServerViewController(id, configHome, loader);
        this.executor = executor;
        this.recoverer = recoverer;
        this.replier = (replier != null ? replier : new DefaultReplier());
        this.verifier = verifier;
		this.proposeRequestVerifier = proposeRequestVerifier;
        this.init();
        this.recoverer.setReplicaContext(replicaCtx);
        this.replier.setReplicaContext(replicaCtx);
		this.reconfigurationListener = reconfigurationListener;
		this.executor.setResponseSender(this);
    }

    // this method initializes the object
    private void init() {
        try {
            cs = new ServerCommunicationSystem(this.SVController, this);
        } catch (Exception ex) {
            logger.error("Failed to initialize replica-to-replica communication system", ex);
            throw new RuntimeException("Unable to build a communication system.");
        }

        if (this.SVController.isInCurrentView()) {
            logger.info("In current view: {}", this.SVController.getCurrentView());
            initTOMLayer(); // initialize the TOM layer
        } else {
            logger.info("Not in current view: {}", this.SVController.getCurrentView());
            
            //Not in the initial view, just waiting for the view where the join has been executed
            logger.info("Waiting for the TTP: {}", this.SVController.getCurrentView());
            waitTTPJoinMsgLock.lock();
            try {
                canProceed.awaitUninterruptibly();
            } finally {
                waitTTPJoinMsgLock.unlock();
            }
            
        }
        initReplica();
    }

	public IReconfigurationListener getReconfigurationListener() {
		return reconfigurationListener;
	}

    public void joinMsgReceived(VMMessage msg) {
        ReconfigureReply r = msg.getReply();

        if (r.getView().isMember(id)) {
            this.SVController.processJoinResult(r);

            initTOMLayer(); // initialize the TOM layer
            cs.updateServersConnections();
            this.cs.joinViewReceived();
            waitTTPJoinMsgLock.lock();
            canProceed.signalAll();
            waitTTPJoinMsgLock.unlock();
        }
    }

    private void initReplica() {
        cs.start();
        repMan = new ReplyManager(SVController.getStaticConf().getNumRepliers(), cs);
    }

    public final void receiveReadonlyMessage(TOMMessage message, MessageContext msgCtx) {
        TOMMessage response;

        // This is used to deliver the requests to the application and obtain a reply to deliver
        //to the clients. The raw decision does not need to be delivered to the recoverable since
        // it is not associated with any consensus instance, and therefore there is no need for
        //applications to log it or keep any proof.

		boolean isReplyHash = message.getReqType() == TOMMessageType.UNORDERED_HASHED_REQUEST
				&& message.getReplyServer() != this.id;
		long start, end;
		start = System.nanoTime();
        response = executor.executeUnordered(id, SVController.getCurrentViewId(), isReplyHash,
				message.getCommonContent(), message.getReplicaSpecificContent(), msgCtx);
		end = System.nanoTime();
		measurementLogger.debug("M-executeUnordered: {}", end - start);

        if (response != null) {
            if (SVController.getStaticConf().getNumRepliers() > 0) {
                repMan.send(response);
            } else {
                cs.send(new int[]{response.getSender()}, response.reply);
            }
        }
    }
        
    /**
     * Stops the service execution at a replica. It will shutdown all threads, stop the requests' timer, and drop all enqueued requests,
     * thus letting the ServiceReplica object be garbage-collected. From the perspective of the rest of the system, this is equivalent
     * to a simple crash fault.
     */
    public void kill() {
        Thread t = new Thread(() -> {
			if (tomLayer != null) {
				tomLayer.shutdown();
			}
		});
        t.start();
    }
        
    /**
     * Cleans the object state and reboots execution. From the perspective of the rest of the system,
     * this is equivalent to a rash followed by a recovery.
     */
    public void restart() {        
        Thread t = new Thread(() -> {
			if (tomLayer != null && cs != null) {
				tomLayer.shutdown();

				try {
					cs.join();
					cs.getServersConn().join();
					tomLayer.join();
					tomLayer.getDeliveryThread().join();

				} catch (InterruptedException ex) {
					logger.error("Interruption while joining threads", ex);
				}

				tomStackCreated = false;
				tomLayer = null;
				cs = null;

				init();
				recoverer.setReplicaContext(replicaCtx);
				replier.setReplicaContext(replicaCtx);

			}
		});
        t.start();
    }

    public void receiveMessages(int[] consId, int[] regencies, int[] leaders, CertifiedDecision[] certifiedDecisions,
								TOMMessage[][] requests) {
        int numRequests = 0;
        int consensusCount = 0;
        List<TOMMessage> toBatch = new ArrayList<>();
        List<MessageContext> msgCtxts = new ArrayList<>();
        boolean noop;
		long start, end;

        for (TOMMessage[] requestsFromConsensus : requests) {
            TOMMessage firstRequest = requestsFromConsensus[0];
            int requestCount = 0;
            noop = true;
            for (TOMMessage request : requestsFromConsensus) {
                
                logger.debug("Processing TOMMessage from client {} with sequence number {} for session {} " +
						"decided in consensus {}", request.getSender(), request.getSequence(), request.getSession(),
						consId[consensusCount]);

                if (request.getViewID() == SVController.getCurrentViewId()) {
                    if (null == request.getReqType()) {
                        throw new RuntimeException("Should never reach here!");
                    } else switch (request.getReqType()) {
                        case ORDERED_REQUEST:
						case ORDERED_HASHED_REQUEST:
                            noop = false;
                            numRequests++;
                            MessageContext msgCtx = new MessageContext(request.getSender(), request.getViewID(),
                                    request.getReqType(), request.getSession(), request.getSequence(),
									request.getOperationId(), request.getReplyServer(),
									request.serializedMessageSignature, firstRequest.timestamp, request.numOfNonces,
									request.seed, regencies[consensusCount], leaders[consensusCount],
									consId[consensusCount], certifiedDecisions[consensusCount].getConsMessages(),
									firstRequest, false, request.hasReplicaSpecificContent(),
									request.getMetadata());
                            if (requestCount + 1 == requestsFromConsensus.length) {
                                msgCtx.setLastInBatch();
                            }   request.deliveryTime = System.nanoTime();
                            if (executor instanceof BatchExecutable) {
                               logger.debug("Batching request from {}", request.getSender());
                                
                                // This is used to deliver the content decided by a consensus instance directly to
                                // a Recoverable object. It is useful to allow the application to create a log and
                                // store the proof associated with decisions (which are needed by replicas
                                // that are asking for a state transfer).
                                if (this.recoverer != null) {
									this.recoverer.Op(msgCtx.getConsensusId(), request.getCommonContent(),
											request.getReplicaSpecificContent(), msgCtx);
								}
                                
                                // deliver requests and contexts to the executor later
                                msgCtxts.add(msgCtx);
                                toBatch.add(request);
                            } else if (executor instanceof SingleExecutable) {
                                logger.debug("Delivering request from {} via SingleExecutable", request.getSender());
                                
                                // This is used to deliver the content decided by a consensus instance directly to
                                // a Recoverable object. It is useful to allow the application to create a log and
                                // store the proof associated with decisions (which are needed by replicas
                                // that are asking for a state transfer).
                                if (this.recoverer != null) {
									this.recoverer.Op(msgCtx.getConsensusId(), request.getCommonContent(),
											request.getReplicaSpecificContent(), msgCtx);
								}
                                
                                // This is used to deliver the requests to the application and obtain a reply to deliver
                                //to the clients. The raw decision is passed to the application in the line above.
								boolean isReplyHash = request.getReqType() == TOMMessageType.ORDERED_HASHED_REQUEST
										&& request.getReplyServer() != this.id;
								start = System.nanoTime();
                                TOMMessage response = ((SingleExecutable) executor).executeOrdered(id,
										SVController.getCurrentViewId(), isReplyHash, request.getCommonContent(),
										request.getReplicaSpecificContent(), msgCtx);
								end = System.nanoTime();
								measurementLogger.debug("M-executeOrdered: {}", end - start);
                                if (response != null && response.reply != null
										&& response.reply.getCommonContent() != null) {
                                    logger.debug("sending reply to {}", response.getSender());
									tomLayer.clientsManager.injectReply(response.getSender(), response.getSequence(),
											response.reply);
                                    replier.manageReply(response, msgCtx);
                                }
                            } else { //this code should never be executed
                                throw new UnsupportedOperationException("Non-existent interface");
                            }   break;
                        case RECONFIG:
                            SVController.enqueueUpdate(request);
							if (reconfigurationListener != null) {
								reconfigurationListener.onReconfigurationRequest(request);
							}
                            break;
                        default: //this code should never be executed
                            throw new RuntimeException("Should never reach here!");
                    }
                } else if (request.getViewID() < SVController.getCurrentViewId()) { 
                    // message sender had an old view, resend the message to
                    // him (but only if it came from consensus, not state transfer)
                    
                    tomLayer.getCommunication().send(new int[]{request.getSender()},
							new TOMMessage(SVController.getStaticConf().getProcessId(),
                            request.getSession(), request.getSequence(), request.getOperationId(),
									TOMUtil.getBytes(SVController.getCurrentView()), false,
									SVController.getCurrentViewId(), request.getReqType()));
                }
                requestCount++;
            }

            // This happens when a consensus finishes but there are no requests to deliver
            // to the application. This can happen if a reconfiguration is issued and is the only
            // operation contained in the batch. The recoverer must be notified about this,
            // hence the invocation of "noop"
            if (noop && this.recoverer != null) {
                logger.debug("Delivering a no-op to the recoverer");
                logger.info("A consensus instance finished, but there were no commands to deliver to the application.");
                logger.info("Notifying recoverable about a blank consensus.");

                byte[][] batch = null;
				byte[][] replicaSpecificContents = null;
                MessageContext[] msgCtx = null;
                if (requestsFromConsensus.length > 0) {
                    //Make new batch to deliver
                    batch = new byte[requestsFromConsensus.length][];
					replicaSpecificContents = new byte[requestsFromConsensus.length][];
                    msgCtx = new MessageContext[requestsFromConsensus.length];

                    //Put messages in the batch
                    int line = 0;
                    for (TOMMessage m : requestsFromConsensus) {
                        batch[line] = m.getCommonContent();
						replicaSpecificContents[line] = m.getReplicaSpecificContent();

                        msgCtx[line] = new MessageContext(m.getSender(), m.getViewID(),
                            m.getReqType(), m.getSession(), m.getSequence(), m.getOperationId(),
                            m.getReplyServer(), m.serializedMessageSignature, firstRequest.timestamp,
                            m.numOfNonces, m.seed, regencies[consensusCount], leaders[consensusCount],
                            consId[consensusCount], certifiedDecisions[consensusCount].getConsMessages(),
								firstRequest, true, m.hasReplicaSpecificContent(), m.getMetadata());
                        msgCtx[line].setLastInBatch();
                        
                        line++;
                    }
                }

                this.recoverer.noOp(consId[consensusCount], batch, replicaSpecificContents, msgCtx);
            }
            
            consensusCount++;
        }

        if (executor instanceof BatchExecutable && numRequests > 0) {
            //Make new batch to deliver
            byte[][] batch = new byte[numRequests][];
			byte[][] individualBatch = new byte[numRequests][];
			boolean[] isReplyHashes = new boolean[numRequests];

            //Put messages in the batch
            int line = 0;
            for (TOMMessage m : toBatch) {
                batch[line] = m.getCommonContent();
				individualBatch[line] = m.getReplicaSpecificContent();
				isReplyHashes[line] = m.getReqType() == TOMMessageType.ORDERED_HASHED_REQUEST
						&& m.getReplyServer() != this.id;
                line++;
            }

            MessageContext[] msgContexts = new MessageContext[msgCtxts.size()];
            msgContexts = msgCtxts.toArray(msgContexts);
            
            //Deliver the batch and wait for replies
            TOMMessage[] replies = ((BatchExecutable) executor).executeBatch(id, SVController.getCurrentViewId(),
					isReplyHashes, batch, individualBatch, msgContexts);

            //Send the replies back to the client
            if (replies != null) {
                for (TOMMessage reply : replies) {
					if (reply == null || reply.reply == null)
						continue;

					tomLayer.clientsManager.injectReply(reply.getSender(), reply.getSequence(), reply.reply);
                    if (SVController.getStaticConf().getNumRepliers() > 0) {
                        logger.debug("Sending reply to {} with sequence number {} and operation ID {} via ReplyManager",
								reply.getSender(), reply.getSequence(), reply.getOperationId());
                        repMan.send(reply);
                    } else {
                        logger.debug("Sending reply to {} with sequence number {} and operation ID {}",
								reply.getSender(), reply.getSequence(), reply.getOperationId());
                        replier.manageReply(reply, null);
                        //cs.send(new int[]{request.getSender()}, request.reply);
                    }
                }
            }
            //DEBUG
            logger.debug("BATCH EXECUTOR END");
        }
    }

	public void sendResponseTo(MessageContext requestMsgCtx, ServiceContent responseToSend) {
		TOMMessageType requestType = requestMsgCtx.getType();
		boolean isReplyHash = (requestType == TOMMessageType.ORDERED_HASHED_REQUEST
				|| requestType == TOMMessageType.UNORDERED_HASHED_REQUEST)
				&& requestMsgCtx.getReplyServer() != this.id;
		int client = requestMsgCtx.getSender();
		int viewID = SVController.getCurrentViewId();
		int session = requestMsgCtx.getSession();
		int sequence = requestMsgCtx.getSequence();
		int operationId = requestMsgCtx.getOperationId();

		byte[] commonResponse = responseToSend.getCommonContent();
		if (isReplyHash) {
			commonResponse = TOMUtil.computeHash(commonResponse);
		}

		byte[] replicaSpecificContent = responseToSend.getReplicaSpecificContent();

		TOMMessage response = requestMsgCtx.recreateTOMMessage(new byte[]{});
		TOMMessage reply = new TOMMessage(id, session, sequence, operationId,
				commonResponse, replicaSpecificContent != null, viewID, requestType);
		reply.setReplicaSpecificContent(replicaSpecificContent);
		response.reply = reply;

		if (response.reply.getCommonContent() != null) {
			logger.debug("sending reply to {}", client);
			tomLayer.clientsManager.injectReply(client, sequence, reply);
			replier.manageReply(response, requestMsgCtx);
		}

	}

    /**
     * This method initializes the object
     */
    private void initTOMLayer() {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }

        if (!SVController.isInCurrentView()) {
            throw new RuntimeException("I'm not an acceptor!");
        }

        // Assemble the total order messaging layer
        MessageFactory messageFactory = new MessageFactory(id);

        Acceptor acceptor = new Acceptor(cs, messageFactory, SVController);
        cs.setAcceptor(acceptor);

        Proposer proposer = new Proposer(cs, messageFactory, SVController);

        ExecutionManager executionManager = new ExecutionManager(SVController, acceptor, proposer, id);

        acceptor.setExecutionManager(executionManager);

        tomLayer = new TOMLayer(executionManager, this, recoverer, acceptor, cs, SVController, verifier,
				proposeRequestVerifier);

        executionManager.setTOMLayer(tomLayer);

        SVController.setTomLayer(tomLayer);

        cs.setTOMLayer(tomLayer);
        cs.setRequestReceiver(tomLayer);

        acceptor.setTOMLayer(tomLayer);

        if (SVController.getStaticConf().isShutdownHookEnabled()) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(tomLayer));
        }
        replicaCtx = new ReplicaContext(cs, SVController);

        tomLayer.start(); // start the layer execution
        tomStackCreated = true;

    }

    /**
     * Obtains the current replica context (getting access to some
     * information and capabilities of the replication engine).
     *
     * @return this replica context
     */
    public final ReplicaContext getReplicaContext() {
        return replicaCtx;
    }
    
    
    /**
     * Obtains the current replica communication system.
     * 
     * @return The replica's communication system
     */
    public ServerCommunicationSystem getServerCommunicationSystem() {
        return cs;
    }

    /**
     * Replica ID
     * @return Replica ID
     */
    public int getId() {
        return id;
    }
}
