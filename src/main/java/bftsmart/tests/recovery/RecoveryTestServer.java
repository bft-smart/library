package bftsmart.tests.recovery;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

/**
 * @author robin
 */
public class RecoveryTestServer extends DefaultSingleRecoverable {
	private final Logger logger = LoggerFactory.getLogger("bftsmart");
	private final byte[] state;
	private long startTime;
	private long numRequests;
	private final Set<Integer> senders;
	private double maxThroughput;

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("USAGE: bftsmart.tests.recovery.RecoveryTestServer <process id> <state size>");
			System.exit(-1);
		}
		int processId = Integer.parseInt(args[0]);
		int stateSize = Integer.parseInt(args[1]);
		new RecoveryTestServer(processId, stateSize);
	}

	public RecoveryTestServer(int processId, int stateSize) {
		state = new byte[stateSize];
		for (int i = 0; i < stateSize; i++) {
			state[i] = (byte) i;
		}
		senders = new HashSet<>(1000);
		new ServiceReplica(processId, this, this);
	}

	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		numRequests++;
		senders.add(msgCtx.getSender());
		ByteBuffer buffer = ByteBuffer.wrap(command);
		Operation op = Operation.getOperation(buffer.get());
		byte[] response = null;
		switch (op) {
			case PUT:
				response = new byte[0];
				break;
			case GET:
				response = state;
				break;
		}
		printMeasurement();
		return response;
	}

	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		numRequests++;
		senders.add(msgCtx.getSender());
		ByteBuffer buffer = ByteBuffer.wrap(command);
		Operation op = Operation.getOperation(buffer.get());
		byte[] response = null;
		if (op == Operation.GET) {
			response = state;
		}
		printMeasurement();
		return response;
	}

	private void printMeasurement() {
		long currentTime = System.nanoTime();
		double deltaTime = (currentTime - startTime) / 1_000_000_000.0;
		if ((int) (deltaTime / 2) > 0) {
			long delta = currentTime - startTime;
			double throughput = numRequests / deltaTime;
			if (throughput > maxThroughput)
				maxThroughput = throughput;
			logger.info("M:(clients[#]|requests[#]|delta[ns]|throughput[ops/s], max[ops/s])>({}|{}|{}|{}|{})",
					senders.size(), numRequests, delta, throughput, maxThroughput);
			numRequests = 0;
			startTime = currentTime;
			senders.clear();
		}
	}

	@Override
	public void installSnapshot(byte[] state) {
	}

	@Override
	public byte[] getSnapshot() {
		return state;
	}
}
