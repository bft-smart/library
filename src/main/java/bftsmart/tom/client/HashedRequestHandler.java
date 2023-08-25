package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.TOMUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HashedRequestHandler extends AbstractRequestHandler {
	private final int replyServer;
	private final List<byte[]> hashReplies;
	private byte[] replyServerResponseHash;

	public HashedRequestHandler(int me, int session, int sequenceId, int operationId, int viewId,
								TOMMessageType requestType, int timeout, int nReplicas,
								int replyQuorumSize, int replyServer) {
		super(me, session, sequenceId, operationId, viewId, requestType, timeout, nReplicas, replyQuorumSize);
		this.replyServer = replyServer;
		this.hashReplies = new ArrayList<>(nReplicas);
	}

	@Override
	public TOMMessage createRequest(byte[] request) {
		TOMMessage requestMessage = new TOMMessage(me, session, sequenceId, operationId, request, viewId, requestType);
		requestMessage.setReplyServer(replyServer);
		return requestMessage;
	}

	@Override
	public void processReply(TOMMessage reply) {
		if (replySenders.contains(reply.getSender())) {//process same reply only once
			return;
		}
		replySenders.add(reply.getSender());

		byte[] replyContentHash;
		if (reply.getSender() == replyServer) {
			response = reply;
			replyContentHash = TOMUtil.computeHash(reply.getContent());
			replyServerResponseHash = replyContentHash;
		} else {
			replyContentHash = reply.getContent();
		}

		hashReplies.add(replyContentHash);
		logger.debug("hash of reply from {}: {}", reply.getSender(), Arrays.toString(replyContentHash));

		//optimization - compare responses after having a quorum of replies and response from reply server
		if (replyServerResponseHash == null || replySenders.size() < replyQuorumSize) {
			return;
		}
		logger.debug("Comparing {} hash responses with response from {}", hashReplies.size(), replyServer);
		int sameContent = 0;
		for (byte[] hash : hashReplies) {
			if (Arrays.equals(hash, replyServerResponseHash)) {
				sameContent++;
				if (sameContent >= replyQuorumSize) {
					semaphore.release();
					return;
				}
			}
		}

		if (replySenders.size() == nReplicas) {
			response = null;
			semaphore.release();
		}
	}
}
