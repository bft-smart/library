package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
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
	protected final int nReplicas;
	protected final int replyQuorumSize;
	protected final Semaphore semaphore;
	protected Set<Integer> replySenders;
	protected TOMMessage response;
	private boolean requestTimeout;

	public AbstractRequestHandler(int me, int session, int sequenceId, int operationId,
								  int viewId, TOMMessageType requestType, int timeout, int nReplicas,
								  int replyQuorumSize) {
		this.me = me;
		this.session = session;
		this.sequenceId = sequenceId;
		this.operationId = operationId;
		this.viewId = viewId;
		this.requestType = requestType;
		this.timeout = timeout;
		this.nReplicas = nReplicas;
		this.replyQuorumSize = replyQuorumSize;
		this.semaphore = new Semaphore(0);
		this.replySenders = new HashSet<>(nReplicas);
	}

	public abstract TOMMessage createRequest(byte[] request);

	public boolean isResponseAvailable() throws InterruptedException {
		return semaphore.tryAcquire(timeout, TimeUnit.SECONDS);
	}

	public void waitForResponse() throws InterruptedException {
		if (!semaphore.tryAcquire(timeout, TimeUnit.SECONDS)) {
			requestTimeout = true;
		}
	}

	/**
	 * This method returns the response.
	 * Call this method after calling waitForResponse() method.
	 * @return Response to the request
	 * @requires all this method after calling waitForResponse() method
	 */
	public TOMMessage getResponse() {
		return response;
	}

	public abstract void processReply(TOMMessage reply);

	public int getSequenceId() {
		return sequenceId;
	}

	public TOMMessageType getRequestType() {
		return requestType;
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
}
