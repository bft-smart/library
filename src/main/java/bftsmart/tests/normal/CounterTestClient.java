
package bftsmart.tests.normal;

import java.io.IOException;
import java.nio.ByteBuffer;

import bftsmart.tom.ServiceProxy;

public class CounterTestClient {

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
			System.out.println("USAGE: bftsmart.tests.counter.CounterTestClient <client id> " +
					"<number of operations> <increment>");
			System.exit(-1);
		}
		int clientId = Integer.parseInt(args[0]);
		int numOperations = Integer.parseInt(args[1]);
		int inc = Integer.parseInt(args[2]);
		int counter = 0;

		try (ServiceProxy proxy = new ServiceProxy(clientId)) {
			System.out.println("Executing experiment");
			for(int i = 0; i<numOperations; i++){
				//send request 
				ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
				buffer.putInt(inc);
				byte[] serializedWriteRequest = buffer.array();
				proxy.invokeOrdered(serializedWriteRequest);
				counter+=inc;

				//check counter was incremented
				ByteBuffer buffer2 = ByteBuffer.allocate(4);
				byte[] serializedReadRequest = buffer2.array();
				byte[] response = proxy.invokeUnordered(serializedReadRequest);
				int counter_received = ByteBuffer.wrap(response).getInt();
				if (counter!=counter_received) {
						throw new IllegalStateException("The response is wrong");
					}	
			}
			System.out.println("Experiment executed");
		}
    }
}


