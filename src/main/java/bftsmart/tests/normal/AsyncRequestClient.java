package bftsmart.tests.normal;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.AsynchServiceProxy;
import bftsmart.tom.RequestContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;

import java.util.Arrays;

public class AsyncRequestClient {

	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("USAGE: bftsmart.tests.normal.AsyncRequestClient <client id> "
					+ "<request type> <number of requests> <request size>");
			System.exit(-1);
		}

		int clientId = Integer.parseInt(args[0]);
		TOMMessageType requestType = TOMMessageType.valueOf(args[1]);
		int nRequests = Integer.parseInt(args[2]);
		int requestSize = Integer.parseInt(args[3]);

		byte[] requestData = new byte[requestSize];

		for (int i = 0; i < requestSize; i++) {
			requestData[i] = (byte) i;
		}

		try (AsynchServiceProxy proxy = new AsynchServiceProxy(clientId)) {
			System.out.println("Executing experiment");
			while (nRequests-- > 0) {
				switch (requestType) {
					case ORDERED_REQUEST:
					case UNORDERED_REQUEST:
					case ORDERED_HASHED_REQUEST:
					case UNORDERED_HASHED_REQUEST:
						proxy.invokeAsynchRequest(requestData, new TestReplyListener(proxy), requestType);
						break;
					default:
						throw new IllegalStateException("Unsupported request type");
				}
			}
		}
		System.out.println("Experiment ended");
	}

	private static class TestReplyListener implements ReplyListener {
		private final AsynchServiceProxy serviceProxy;
		private final TOMMessage[] replies;
		private int index;

		public TestReplyListener(AsynchServiceProxy serviceProxy) {
			this.replies = new TOMMessage[serviceProxy.getViewManager().getCurrentViewN()];
			this.serviceProxy = serviceProxy;
		}

		@Override
		public void reset() {
			Arrays.fill(replies, null);
			index = 0;
		}

		@Override
		public void replyReceived(RequestContext context, TOMMessage reply) {
			if (index == 0) {
				replies[index++] = reply;
				return;
			}
			int sameContent = 1;
			for (int i = 0; i < index; i++) {
				if (Arrays.equals(reply.getContent(), replies[i].getContent())) {
					sameContent++;
				}
			}
			if (sameContent >= serviceProxy.getViewManager().getReplyQuorum()) {
				byte[] response = reply.getContent();
				for (int i = 0; i < response.length; i++) {
					if (response[i] != (byte) i) {
						throw new IllegalStateException("Server response is wrong");
					}
				}
				serviceProxy.cleanAsynchRequest(context.getOperationId());
			} else {
				replies[index++] = reply;
			}

		}
	}
}
