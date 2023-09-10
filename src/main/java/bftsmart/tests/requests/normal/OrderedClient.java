package bftsmart.tests.requests.normal;

import bftsmart.tests.requests.AbstractSimpleServiceClient;
import bftsmart.tom.ServiceProxy;

public class OrderedClient extends AbstractSimpleServiceClient {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("USAGE: bftsmart.tests.requests.normal.OrderedClient <client id> " +
					"<number of operations> <request size>");
			System.exit(-1);
		}
		new OrderedClient(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}

	public OrderedClient(int clientId, int numOperations, int requestSize) {
		super(clientId, numOperations, requestSize);
	}

	@Override
	public boolean executeRequest(byte[] data, byte[] serializedWriteRequest,
								  byte[] serializedReadRequest, ServiceProxy proxy) {
		byte[] response = proxy.invokeOrdered(serializedWriteRequest);
		return response != null && response.length == 0;
	}
}
