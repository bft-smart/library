package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;

import java.util.*;

public class NormalRequestHandler extends AbstractRequestHandler {
	private final Comparator<byte[]> comparator;
	private final Extractor responseExtractor;

	public NormalRequestHandler(int me, int session, int sequenceId, int operationId, int viewId,
								TOMMessageType requestType, int timeout, int[] replicas,
								int replyQuorumSize, Comparator<byte[]> comparator, Extractor responseExtractor) {
		super(me, session, sequenceId, operationId, viewId, requestType, timeout, replicas, replyQuorumSize);
		this.comparator = comparator;
		this.responseExtractor = responseExtractor;
	}

	@Override
	public TOMMessage createRequest(byte[] request) {
		return new TOMMessage(me, session, sequenceId, operationId, request, viewId, requestType);
	}

	@Override
	public TOMMessage processReply(TOMMessage reply, int lastSenderIndex) {
		//optimization - compare responses after having a quorum of replies
		if (replySenders.size() < replyQuorumSize) {
			return null;
		}

		int sameContent = 0;
		logger.debug("Comparing {} responses with response from {}", replySenders.size(), reply.getSender());
		for (TOMMessage msg : replies) {
			if (msg == null) {
				continue;
			}
			if (comparator.compare(msg.getContent(), reply.getContent()) == 0) {
				sameContent++;
				if (sameContent >= replyQuorumSize) {
					return responseExtractor.extractResponse(replies, sameContent, lastSenderIndex);
				}
			}
		}

		return null;
	}

	@Override
	public void printState() {

	}
}
