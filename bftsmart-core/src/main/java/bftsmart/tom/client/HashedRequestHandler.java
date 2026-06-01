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
	private int fullResponseIndex;

	public HashedRequestHandler(int me, int session, int sequenceId, int operationId, int viewId,
								TOMMessageType requestType, int timeout, int[] replicas,
								int replyQuorumSize, int replyServer) {
		super(me, session, sequenceId, operationId, viewId, requestType, timeout, replicas, replyQuorumSize);
		this.replyServer = replyServer;
		this.hashReplies = new ArrayList<>(replicas.length);
		this.fullResponseIndex = -1;
	}

	@Override
	public TOMMessage createRequest(byte[] request) {
		TOMMessage requestMessage = new TOMMessage(me, session, sequenceId, operationId, request, viewId, requestType);
		requestMessage.setReplyServer(replyServer);
		return requestMessage;
	}

	@Override
	public TOMMessage processReply(TOMMessage reply, int lastSenderIndex) {
		byte[] replyContentHash;
		if (reply.getSender() == replyServer) {
			fullResponseIndex = lastSenderIndex;
			replyContentHash = TOMUtil.computeHash(reply.getContent());
			replyServerResponseHash = replyContentHash;
		} else {
			replyContentHash = reply.getContent();
		}

		hashReplies.add(replyContentHash);
		logger.debug("hash of reply from {}: {}", reply.getSender(), Arrays.toString(replyContentHash));

		//optimization - compare responses after having a quorum of replies and response from reply server
		if (replyServerResponseHash == null || replySenders.size() < replyQuorumSize) {
			return null;
		}
		logger.debug("Comparing {} hash responses with response from {}", replySenders.size(), replyServer);
		int sameContent = 0;
		for (byte[] hash : hashReplies) {
			if (Arrays.equals(hash, replyServerResponseHash)) {
				sameContent++;
				if (sameContent >= replyQuorumSize) {
					return replies[fullResponseIndex];
				}
			}
		}

		return null;
	}

	@Override
	public void printState() {
		for (int i = 0; i < hashReplies.size(); i++) {
			logger.info("hash of reply from {}: {} | {}", i, Arrays.hashCode(hashReplies.get(i)),
					Arrays.toString(hashReplies.get(i)));
		}
		logger.info("Have received response from reply server {}: {}", replyServer, replyServerResponseHash != null);
	}
}
