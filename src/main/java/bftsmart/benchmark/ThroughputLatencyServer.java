package bftsmart.benchmark;

import bftsmart.tests.recovery.Operation;
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
public class ThroughputLatencyServer extends DefaultSingleRecoverable {
	private final Logger logger = LoggerFactory.getLogger("bftsmart");
	private byte[] state;
	private long startTime;
	private long numRequests;
	private final Set<Integer> senders;
	private double maxThroughput;

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("USAGE: bftsmart.benchmark.ThroughputLatencyServer <process id>");
			System.exit(-1);
		}
		int processId = Integer.parseInt(args[0]);
		new ThroughputLatencyServer(processId);
	}

	public ThroughputLatencyServer(int processId) {
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
				int size = buffer.getInt();
				state = new byte[size];
				buffer.get(state);
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
		this.state = state;
	}

	@Override
	public byte[] getSnapshot() {
		return state == null ? new byte[0] : state;
	}
}
