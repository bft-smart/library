package bftsmart.tests.requests.hashed;

import bftsmart.tests.requests.AbstractSimpleServiceClient;
import bftsmart.tom.ServiceProxy;

import java.util.Arrays;

public class UnorderedHashedClient extends AbstractSimpleServiceClient {

	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("USAGE: bftsmart.tests.requests.hashed.UnorderedHashedClient <client id> " +
					"<number of operations> <request size>");
			System.exit(-1);
		}
		new UnorderedHashedClient(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}

	public UnorderedHashedClient(int clientId, int numOperations, int requestSize) {
		super(clientId, numOperations, requestSize);
	}

	@Override
	public boolean executeRequest(byte[] data, byte[] serializedWriteRequest,
								  byte[] serializedReadRequest, ServiceProxy proxy) {
		byte[] response = proxy.invokeUnorderedHashed(serializedReadRequest);
		return Arrays.equals(response, data);
	}
}
