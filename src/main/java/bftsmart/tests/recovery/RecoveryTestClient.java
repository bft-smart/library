package bftsmart.tests.recovery;

import bftsmart.tom.ServiceProxy;

import java.nio.ByteBuffer;

/**
 * @author robin
 */
public class RecoveryTestClient {
	public static void main(String[] args) {
		if (args.length != 4) {
			System.out.println("USAGE: bftsmart.tests.recovery.RecoveryTestClient <client id> " +
					"<number of operations> <request size> <isWrite?>");
			System.exit(-1);
		}
		int clientId = Integer.parseInt(args[0]);
		int numOperations = Integer.parseInt(args[1]);
		int requestSize = Integer.parseInt(args[2]);
		boolean isWrite = Boolean.parseBoolean(args[3]);

		try (ServiceProxy proxy = new ServiceProxy(clientId)) {
			if (isWrite) {
				byte[] data = new byte[requestSize];
				for (int i = 0; i < requestSize; i++) {
					data[i] = (byte) i;
				}
				ByteBuffer buffer = ByteBuffer.allocate(1 + Integer.BYTES + requestSize);
				buffer.put((byte) Operation.PUT.ordinal());
				buffer.putInt(requestSize);
				buffer.put(data);
				byte[] serializedWriteRequest = buffer.array();
				System.out.println("Executing experiment");
				for (int i = 0; i < numOperations; i++) {
					proxy.invokeOrdered(serializedWriteRequest);
				}
			} else {
				ByteBuffer buffer = ByteBuffer.allocate(1);
				buffer.put((byte) Operation.GET.ordinal());
				byte[] serializedReadRequest = buffer.array();
				System.out.println("Executing experiment");
				for (int i = 0; i < numOperations; i++) {
					byte[] response = proxy.invokeUnordered(serializedReadRequest);
					for (int j = 0; j < requestSize; j++) {
						if (response[j] != (byte) j) {
							throw new IllegalStateException("The response is wrong");
						}
					}
				}
			}
		}

	}
}
