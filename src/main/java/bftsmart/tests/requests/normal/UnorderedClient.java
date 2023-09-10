package bftsmart.tests.requests.normal;

import bftsmart.tests.requests.AbstractSimpleServiceClient;
import bftsmart.tom.ServiceProxy;

import java.util.Arrays;

public class UnorderedClient extends AbstractSimpleServiceClient {
	public static void main(String[] args) {
		if (args.length != 3) {
			System.out.println("USAGE: bftsmart.tests.requests.normal.UnorderedClient <client id> " +
					"<number of operations> <request size>");
			System.exit(-1);
		}
		new UnorderedClient(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
	}

	public UnorderedClient(int clientId, int numOperations, int requestSize) {
		super(clientId, numOperations, requestSize);
	}

	@Override
	public boolean executeRequest(byte[] data, byte[] serializedWriteRequest,
								  byte[] serializedReadRequest, ServiceProxy proxy) {
		byte[] response = proxy.invokeUnorderedHashed(serializedReadRequest);
		return Arrays.equals(response, data);
	}
}
