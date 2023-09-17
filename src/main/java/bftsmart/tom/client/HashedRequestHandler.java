package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.TOMUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HashedRequestHandler extends AbstractRequestHandler {
	private final int replyServer;
	private final List<byte[]> hashReplies;
	private final Extractor responseExtractor;
	private byte[] replyServerResponseHash;
	private int fullResponseIndex;

	public HashedRequestHandler(int me, int session, int sequenceId, int operationId, int viewId,
								TOMMessageType requestType, int timeout, int[] replicas,
								int replyQuorumSize, int replyServer, Extractor responseExtractor) {
		super(me, session, sequenceId, operationId, viewId, requestType, timeout, replicas, replyQuorumSize);
		this.replyServer = replyServer;
		this.hashReplies = new ArrayList<>(replicas.length);
		this.responseExtractor = responseExtractor;
		this.fullResponseIndex = -1;
	}

	@Override
	public TOMMessage createRequest(byte[] request, boolean hasReplicaSpecificContent, byte metadata) {
		TOMMessage requestMessage = new TOMMessage(me, session, sequenceId, operationId, request,
				hasReplicaSpecificContent, metadata, viewId, requestType);
		requestMessage.setReplyServer(replyServer);
		return requestMessage;
	}

	@Override
	public void processReply(TOMMessage reply, int lastReceivedIndex) {
		byte[] replyContentHash;
		if (reply.getSender() == replyServer) {
			fullResponseIndex = lastReceivedIndex;
			replyContentHash = TOMUtil.computeHash(reply.getCommonContent());
			replyServerResponseHash = replyContentHash;
		} else {
			replyContentHash = reply.getCommonContent();
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
					response = responseExtractor.extractResponse(replies, sameContent, fullResponseIndex);
					response.setViewID(reply.getViewID());
					semaphore.release();
					return;
				}
			}
		}

		if (replySenders.size() == replicas.length) {
			response = null;
			semaphore.release();
		}
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
