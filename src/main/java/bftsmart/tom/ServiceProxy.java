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
package bftsmart.tom;

import bftsmart.reconfiguration.IClientSideReconfigurationListener;
import bftsmart.reconfiguration.ReconfigureReply;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.client.AbstractRequestHandler;
import bftsmart.tom.client.HashedRequestHandler;
import bftsmart.tom.client.NormalRequestHandler;
import bftsmart.tom.core.TOMSender;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class implements a TOMSender and represents a proxy to be used on the
 * client side of the replicated system.
 * It sends a request to the replicas, receives the reply, and delivers it to
 * the application.
 */
public class ServiceProxy extends TOMSender {
	private final Logger logger = LoggerFactory.getLogger("bftsmart.proxy");

	// Locks for send requests and receive replies
	protected ReentrantLock canReceiveLock = new ReentrantLock();
	protected ReentrantLock canSendLock = new ReentrantLock();
	private int invokeTimeout;
	private final Comparator<ServiceContent> comparator;
	private final Extractor extractor;
	private final HashedExtractor hashedExtractor;
	private final Random rand = new Random(System.currentTimeMillis());
	private int invokeUnorderedHashedTimeout = 10;

	private AbstractRequestHandler requestHandler; //Active request context
	private ServiceResponse response;
	private final IClientSideReconfigurationListener reconfigurationListener;

	/**
	 * Constructor
	 *
	 * @see #ServiceProxy(int, String, Comparator, Extractor, KeyLoader)
	 */
	public ServiceProxy(int processId) {
		this(processId, null, null, null, null, null, null);
	}

	public ServiceProxy(int processId, IClientSideReconfigurationListener reconfigurationListener) {
		this(processId, null, null, null, null, null, reconfigurationListener);
	}

	/**
	 * Constructor
	 *
	 * @see #ServiceProxy(int, String, Comparator, Extractor, KeyLoader)
	 */
	public ServiceProxy(int processId, String configHome) {
		this(processId, configHome, null, null, null, null, null);
	}

	/**
	 * Constructor
	 *
	 * @see #ServiceProxy(int, String, Comparator, Extractor, KeyLoader)
	 */
	public ServiceProxy(int processId, String configHome, KeyLoader loader) {
		this(processId, configHome, null, null, null, loader, null);
	}

	/**
	 * Constructor
	 *
	 * @param processId Process id for this client (should be different from replicas)
	 * @param configHome Configuration directory for BFT-SMART
	 * @param replyComparator Used for comparing replies from different servers
	 *                        to extract one returned by f+1
	 * @param replyExtractor Used for extracting the response from the matching
	 *                       quorum of replies
	 * @param loader Used to load signature keys from disk
	 */
	public ServiceProxy(int processId, String configHome,
						Comparator<ServiceContent> replyComparator, Extractor replyExtractor, KeyLoader loader) {
		this(processId, configHome, replyComparator, replyExtractor, null, loader, null);
	}

	public ServiceProxy(int processId, String configHome,
						Comparator<ServiceContent> replyComparator, Extractor replyExtractor,
						HashedExtractor hashedReplyExtractor) {
		this(processId, configHome, replyComparator, replyExtractor, hashedReplyExtractor, null, null);
	}

	public ServiceProxy(int processId, String configHome,
						Comparator<ServiceContent> replyComparator, Extractor replyExtractor,
						HashedExtractor hashedReplyExtractor,
						IClientSideReconfigurationListener reconfigurationListener) {
		this(processId, configHome, replyComparator, replyExtractor, hashedReplyExtractor,
				null, reconfigurationListener);
	}

	public ServiceProxy(int processId, String configHome,
						Comparator<ServiceContent> replyComparator, Extractor replyExtractor,
						HashedExtractor hashedReplyExtractor, KeyLoader loader,
						IClientSideReconfigurationListener reconfigurationListener) {
		super(processId, configHome, loader);
		this.invokeTimeout = getViewManager().getStaticConf().getClientInvokeOrderedTimeout();

		comparator = (replyComparator != null) ? replyComparator
				: (o1, o2) -> Arrays.equals(o1.getCommonContent(), o2.getCommonContent()) ? 0 : -1;
		extractor = (replyExtractor != null) ? replyExtractor :
				(replies, sameContent, lastReceived)
						-> new ServiceResponse(replies[lastReceived].getContent().getCommonContent());
		hashedExtractor = (hashedReplyExtractor != null) ? hashedReplyExtractor :
				(replies, fullReply, fullReplyHash, sameContent)
						-> new ServiceResponse(fullReply.getContent().getCommonContent());
		this.reconfigurationListener = reconfigurationListener;
	}

	/**
	 * Get the amount of time (in seconds) that this proxy will wait for
	 * servers replies before returning null.
	 *
	 * @return the timeout value in seconds
	 */
	public int getInvokeTimeout() {
		return invokeTimeout;
	}

	/**
	 * Get the amount of time (in seconds) that this proxy will wait for
	 * servers unordered hashed replies before returning null.
	 *
	 * @return the timeout value in seconds
	 */
	public int getInvokeUnorderedHashedTimeout() {
		return invokeUnorderedHashedTimeout;
	}

	/**
	 * Set the amount of time (in seconds) that this proxy will wait for
	 * servers replies before returning null.
	 *
	 * @param invokeTimeout the timeout value to set
	 */
	public void setInvokeTimeout(int invokeTimeout) {
		this.invokeTimeout = invokeTimeout;
	}

	/**
	 * Set the amount of time (in seconds) that this proxy will wait for
	 * servers unordered hashed replies before returning null.
	 *
	 * @param timeout the timeout value to set
	 */
	public void setInvokeUnorderedHashedTimeout(int timeout) {
		this.invokeUnorderedHashedTimeout = timeout;
	}

	/**
	 * This method sends an ordered request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invokeOrdered(byte[] request) {
		return invoke(request, TOMMessageType.ORDERED_REQUEST);
	}

	/**
	 * This method sends an ordered request to the replicas, and returns the related reply.
	 * This method chooses randomly one replica to send the complete response, while the others
	 * only send a hash of that response.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invokeOrderedHashed(byte[] request) {
		return invoke(request, TOMMessageType.ORDERED_HASHED_REQUEST);
	}

	/**
	 * This method sends an unordered request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invokeUnordered(byte[] request) {
		return invoke(request, TOMMessageType.UNORDERED_REQUEST);
	}

	/**
	 * This method sends an unordered request to the replicas, and returns the related reply.
	 * This method chooses randomly one replica to send the complete response, while the others
	 * only send a hash of that response.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request to be sent
	 * @return The reply from the replicas related to request
	 */
	public byte[] invokeUnorderedHashed(byte[] request) {
		return invoke(request, TOMMessageType.UNORDERED_HASHED_REQUEST);
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param request Request to be sent
	 * @param reqType ORDERED_REQUEST/ORDERED_HASHED_REQUEST/UNORDERED_REQUEST/UNORDERED_HASHED_REQUEST
	 *                   for normal requests, and RECONFIG for reconfiguration requests.
	 *
	 * @return The reply from the replicas related to request
	 */

	public byte[] invoke(byte[] request, TOMMessageType reqType) {
		ServiceResponse response = invoke(reqType, request, null, (byte) -1);
		if (response == null) {
			return null;
		} else {
			return response.getContent();
		}
	}

	/**
	 * This method sends a request to the replicas, and returns the related reply.
	 * If the servers take more than invokeTimeout seconds the method returns null.
	 * This method is thread-safe.
	 *
	 * @param reqType ORDERED_REQUEST/ORDERED_HASHED_REQUEST/UNORDERED_REQUEST/UNORDERED_HASHED_REQUEST
	 *                for normal requests, and RECONFIG for reconfiguration requests.
	 * @param request Request to be sent
	 * @param replicaSpecificContents Map with replica specific contents
	 * @param metadata Metadata to be sent with the request
	 * @return The reply from the replicas related to request
	 */
	public ServiceResponse invoke(TOMMessageType reqType, byte[] request, Map<Integer, byte[]> replicaSpecificContents,
								  byte metadata) {
		try {
			canSendLock.lock();
			response = null;

			AbstractRequestHandler requestHandler = createRequestHandler(reqType);
			this.requestHandler = requestHandler;

			TOMMessage requestMessage = requestHandler.createRequest(request,
					replicaSpecificContents != null, metadata);

			logger.debug("Sending request ({}) with seqId = {}", reqType, requestHandler.getSequenceId());
			TOMulticast(requestMessage, replicaSpecificContents);

			logger.debug("Expected number of matching replies: {}", requestHandler.getReplyQuorumSize());

			// This instruction blocks the thread, until a response is obtained.
			// The thread will be unblocked when the method replyReceived is invoked
			// by the client side communication system
			requestHandler.waitForResponse();

			if (requestHandler.isRequestTimeout()) {
				logger.warn("###### TIMEOUT ({}s) OF REQUEST {} | seqId: {} | replies received: {} ######",
						invokeTimeout, reqType, requestHandler.getSequenceId(),
						requestHandler.getNumberReceivedReplies());
				if (reqType == TOMMessageType.UNORDERED_HASHED_REQUEST || reqType == TOMMessageType.UNORDERED_REQUEST) {
					return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
				} else {
					return null;
				}
			}

			logger.debug("Response extracted: " + response);

			if (response == null) {
				//the response can be null if n-f replies are received but there isn't
				//a replyQuorumSize of matching replies
				logger.debug("Received n-f replies and no response could be extracted.");

				if (reqType == TOMMessageType.UNORDERED_REQUEST || reqType == TOMMessageType.UNORDERED_HASHED_REQUEST) {
					//invoke the operation again, whitout the read-only flag
					logger.debug("###################RETRY#######################");
					return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
				} else {
					requestHandler.printState();
					throw new RuntimeException("Received n-f replies without f+1 of them matching.");
				}
			} else {
				if (response.getViewID() == getViewManager().getCurrentViewId()) {// normal operation
					return response;
				} else if (response.getViewID() > getViewManager().getCurrentViewId()) {
					if (reqType == TOMMessageType.ORDERED_REQUEST) {
						reconfigureTo((View) TOMUtil.getObject(response.getContent()));
						return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
					} else if (reqType == TOMMessageType.UNORDERED_REQUEST
							|| reqType == TOMMessageType.UNORDERED_HASHED_REQUEST) {
						// Ignore the response and request again because servers are in a later view
						return invoke(TOMMessageType.ORDERED_REQUEST, request, replicaSpecificContents, metadata);
					} else {// Reply to a reconfigure request!
						logger.debug("Reconfiguration request' reply received!");
						Object r = TOMUtil.getObject(response.getContent());
						if (r instanceof View) { //did not execute the request because it is using an outdated view
							reconfigureTo((View) r);
							return invoke(reqType, request, replicaSpecificContents, metadata);
						}  else if (r instanceof ReconfigureReply) { //reconfiguration executed!
							reconfigureTo(((ReconfigureReply) r).getView());
							return response;
						} else{
							logger.error("Unknown response type");
						}
					}
				} else {
					logger.error("My view is ahead of the servers' view. This should never happen!");
				}
				return null;
			}

		} catch (InterruptedException e) {
			logger.error("Failed to wait for a response. Returning null as response.", e);
			return null;
		} finally {
			canSendLock.unlock(); //always release lock
		}
	}

	/**
	 * Creates a request handler based on the request type
	 * @param requestType Request type
	 * @return Request handler
	 */
	private AbstractRequestHandler createRequestHandler(TOMMessageType requestType) {
		AbstractRequestHandler requestHandler;
		int replyQuorumSize = getReplyQuorum();// size of the reply quorum
		int sequenceId = generateRequestId(requestType);
		int operationId = generateOperationId();

		if (requestType == TOMMessageType.UNORDERED_HASHED_REQUEST || requestType == TOMMessageType.ORDERED_HASHED_REQUEST) {
			int replyServer = getRandomlyServerId();
			logger.debug("[Client {}] replyServerId({}) pos({})", getProcessId(), replyServer,
					getViewManager().getCurrentViewPos(replyServer));
			requestHandler = new HashedRequestHandler(
					getProcessId(),
					getSession(),
					sequenceId,
					operationId,
					getViewManager().getCurrentViewId(),
					requestType,
					invokeTimeout,
					getViewManager().getCurrentViewProcesses(),
					replyQuorumSize,
					replyServer,
					hashedExtractor
			);
		} else { // ORDERED_REQUEST or UNORDERED_REQUEST
			requestHandler = new NormalRequestHandler(
					getProcessId(),
					getSession(),
					sequenceId,
					operationId,
					getViewManager().getCurrentViewId(),
					requestType,
					invokeTimeout,
					getViewManager().getCurrentViewProcesses(),
					replyQuorumSize,
					comparator,
					extractor
			);
		}

		return requestHandler;
	}

	//******* EDUARDO BEGIN **************//
	protected void reconfigureTo(View v) {
		logger.debug("Installing a most up-to-date view with id=" + v.getId());
		getViewManager().reconfigureTo(v);
		getViewManager().getViewStore().storeView(v);
		getCommunicationSystem().updateConnections();
		if (reconfigurationListener != null)
			reconfigurationListener.onReconfiguration(v);
	}
	//******* EDUARDO END **************//

	/**
	 * This is the method invoked by the client side communication system.
	 *
	 * @param reply The reply delivered by the client side communication system
	 */
	@Override
	public void replyReceived(TOMMessage reply) {
		logger.debug("Synchronously received reply from {} with sequence number {}", reply.getSender(),
				reply.getSequence());
		try {
			canReceiveLock.lock();
			if (requestHandler == null) {//no message being expected
				logger.debug("throwing out request: sender = {} reqId = {}", reply.getSender(), reply.getSequence());
				return;
			}

			int replicaPosition = getViewManager().getCurrentViewPos(reply.getSender());
			if (replicaPosition < 0) {
				return;
			}

			response = requestHandler.processReply(reply);
			if (response != null) {
				requestHandler.responseIsReady();
				requestHandler = null;
			}
		} catch (Exception ex) {
			logger.error("Problem processing reply", ex);
		} finally {
			canReceiveLock.unlock();
		}
	}

	/**
	 * Retrieves the required quorum size for the amount of replies
	 *
	 * @return The quorum size for the amount of replies
	 */
	protected int getReplyQuorum() {
		if (getViewManager().getStaticConf().isBFT()) {
			return ((getViewManager().getCurrentViewN() + getViewManager().getCurrentViewF()) / 2) + 1;
		} else {
			return ((getViewManager().getCurrentViewN()) / 2) + 1;
		}
	}

	private int getRandomlyServerId(){
		int numServers = super.getViewManager().getCurrentViewProcesses().length;
		int pos = rand.nextInt(numServers);

		return super.getViewManager().getCurrentViewProcesses()[pos];
	}
}