package bftsmart.tom.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;

import java.util.*;

public class NormalRequestHandler extends AbstractRequestHandler {
	private final List<TOMMessage> replies;
	private final TOMMessage[] replyQuorum;
	private final Comparator<byte[]> comparator;
	private final Extractor responseExtractor;

	public NormalRequestHandler(int me, int session, int sequenceId, int operationId, int viewId,
								TOMMessageType requestType, int timeout, int nReplicas,
								int replyQuorumSize, Comparator<byte[]> comparator, Extractor responseExtractor) {
		super(me, session, sequenceId, operationId, viewId, requestType, timeout, nReplicas, replyQuorumSize);
		this.replies = new ArrayList<>(nReplicas);
		this.replyQuorum = new TOMMessage[replyQuorumSize];
		this.comparator = comparator;
		this.responseExtractor = responseExtractor;
	}

	@Override
	public TOMMessage createRequest(byte[] request) {
		return new TOMMessage(me, session, sequenceId, operationId, request, viewId, requestType);
	}

	@Override
	public void processReply(TOMMessage reply) {
		if (replySenders.contains(reply.getSender())) {//process same reply only once
			return;
		}
		replySenders.add(reply.getSender());
		replies.add(reply);

		//optimization - compare responses after having a quorum of replies
		if (replySenders.size() < replyQuorumSize) {
			return;
		}

		int sameContent = 0;
		logger.debug("Comparing {} responses with response from {}", replies.size(), reply.getSender());
		for (TOMMessage msg : replies) {
			if (comparator.compare(msg.getContent(), reply.getContent()) == 0) {
				replyQuorum[sameContent] = msg;
				sameContent++;
				if (sameContent >= replyQuorumSize) {
					response = responseExtractor.extractResponse(replyQuorum, sameContent, sameContent - 1);
					semaphore.release();
					return;
				}
			}
		}

		if (replySenders.size() == nReplicas) {
			semaphore.release();
		}

	}
}
