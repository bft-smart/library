package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public abstract class AbstractRequestHandler {
	protected final Logger logger = LoggerFactory.getLogger("bftsmart.proxy");
	protected final int me;
	protected final int session;
	protected final int sequenceId;
	protected final int operationId;
	protected final int viewId;
	protected final TOMMessageType requestType;
	private final int timeout;
	private final int[] replicas;
	private final Map<Integer, Integer> replicaIndex;
	protected final TOMMessage[] replies;
	protected final int replyQuorumSize;
	private final Semaphore semaphore;
	protected Set<Integer> replySenders;
	private TOMMessage response;
	private boolean requestTimeout;

	public AbstractRequestHandler(int me, int session, int sequenceId, int operationId,
								  int viewId, TOMMessageType requestType, int timeout, int[] replicas,
								  int replyQuorumSize) {
		this.me = me;
		this.session = session;
		this.sequenceId = sequenceId;
		this.operationId = operationId;
		this.viewId = viewId;
		this.requestType = requestType;
		this.timeout = timeout;
		this.replicas = replicas;
		this.replies = new TOMMessage[replicas.length];
		this.replyQuorumSize = replyQuorumSize;
		this.semaphore = new Semaphore(0);
		this.replySenders = new HashSet<>(replicas.length);
		this.replicaIndex = new HashMap<>(replicas.length);
		for (int i = 0; i < replicas.length; i++) {
			replicaIndex.put(replicas[i], i);
		}
	}

	public abstract TOMMessage createRequest(byte[] request);

	public void waitForResponse() throws InterruptedException {
		if (!semaphore.tryAcquire(timeout, TimeUnit.SECONDS)) {
			requestTimeout = true;
		}
	}

	/**
	 * This method returns the response.
	 * Call this method after calling waitForResponse() method.
	 * @return Response to the request
	 * @requires call this method after calling waitForResponse() method
	 */
	public TOMMessage getResponse() {
		return response;
	}

	public void processReply(TOMMessage reply) {
		if (response != null) {//no message being expected
			logger.debug("throwing out request: sender = {} reqId = {}", reply.getSender(), reply.getSequence());
			return;
		}
		logger.debug("(current reqId: {}) Received reply from {} with reqId: {}", sequenceId, reply.getSender(),
				reply.getSequence());
		Integer lastSenderIndex = replicaIndex.get(reply.getSender());
		if (lastSenderIndex == null) {
			logger.error("Received reply from unknown replica {}", reply.getSender());
			return;
		}
		if (sequenceId != reply.getSequence() || requestType != reply.getReqType()) {
			logger.debug("Ignoring reply from {} with reqId {}. Currently wait reqId {} of type {}",
					reply.getSender(), reply.getSequence(), sequenceId, requestType);
			return;
		}
		if (replySenders.contains(reply.getSender())) {//process same reply only once
			return;
		}
		replySenders.add(reply.getSender());
		replies[lastSenderIndex] = reply;

		response = processReply(reply, lastSenderIndex);
		if (response != null || replySenders.size() == replicas.length) {
			semaphore.release();
 		}
	}

	public abstract TOMMessage processReply(TOMMessage reply, int lastSenderIndex);

	public int getSequenceId() {
		return sequenceId;
	}

	public int getNumberReceivedReplies() {
		return replySenders.size();
	}

	public int getReplyQuorumSize() {
		return replyQuorumSize;
	}

	/**
	 *
	 * Call this method after calling waitForResponse().
	 * @return true if request timeout or false otherwise.
	 * @requires Call this method after calling waitForResponse().
	 */
	public boolean isRequestTimeout() {
		return requestTimeout;
	}

	public abstract void printState();
}
