package bftsmart.tests.requests;

import bftsmart.tests.util.Operation;
import bftsmart.tom.ServiceProxy;

import java.nio.ByteBuffer;

public abstract class AbstractSimpleServiceClient {

	public AbstractSimpleServiceClient(int clientId, int numOperations, int requestSize) {
		byte[] data = new byte[requestSize];
		for (int i = 0; i < requestSize; i++) {
			data[i] = (byte) i;
		}

		ByteBuffer writeBuffer = ByteBuffer.allocate(1 + Integer.BYTES + requestSize);
		writeBuffer.put((byte) Operation.PUT.ordinal());
		writeBuffer.putInt(requestSize);
		writeBuffer.put(data);
		byte[] serializedWriteRequest = writeBuffer.array();

		ByteBuffer readBuffer = ByteBuffer.allocate(1);
		readBuffer.put((byte) Operation.GET.ordinal());
		byte[] serializedReadRequest = readBuffer.array();

		try (ServiceProxy proxy = new ServiceProxy(clientId)) {
			System.out.println("Executing experiment");
			while (numOperations-- > 0) {
				boolean wasSuccess = executeRequest(data, serializedWriteRequest, serializedReadRequest, proxy);
				if (!wasSuccess) {
					throw new RuntimeException("Failed to execute request");
				}
			}
		}
		System.out.println("Experiment ended");
	}

	public abstract boolean executeRequest(byte[] data, byte[] serializedWriteRequest, byte[] serializedReadRequest,
								  ServiceProxy proxy);
}
