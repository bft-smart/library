package bftsmart.tests.normal;

import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;

public class SynchronousRequestClient {

	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("USAGE: bftsmart.tests.normal.NormalRequestClient <client id> "
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

		try (ServiceProxy proxy = new ServiceProxy(clientId)) {
			System.out.println("Executing experiment");
			byte[] response;
			while (nRequests-- > 0) {
				switch (requestType) {
					case ORDERED_REQUEST:
						response = proxy.invokeOrdered(requestData);
						break;
					case UNORDERED_REQUEST:
						response = proxy.invokeUnordered(requestData);
						break;
					case ORDERED_HASHED_REQUEST:
						response = proxy.invokeOrderedHashed(requestData);
						break;
					case UNORDERED_HASHED_REQUEST:
						response = proxy.invokeUnorderedHashed(requestData);
						break;
					default:
						throw new IllegalStateException("Unsupported request type");
				}
				for (int i = 0; i < response.length; i++) {
					if (response[i] != (byte) i) {
						throw new IllegalStateException("Server response is wrong");
					}
				}
			}
		}
		System.out.println("Experiment ended");
	}
}
