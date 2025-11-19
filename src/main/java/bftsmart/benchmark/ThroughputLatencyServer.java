package bftsmart.benchmark;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * @author robin
 */
public class ThroughputLatencyServer extends DefaultSingleRecoverable {
	private final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private byte[] state;
	private long startTime;
	private long numRequests;
	private final Set<Integer> senders;
	private double maxThroughput;

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("USAGE: bftsmart.benchmark.ThroughputLatencyServer <process id> <state size>");
			System.exit(-1);
		}
		int processId = Integer.parseInt(args[0]);
		int stateSize = Integer.parseInt(args[1]);
		new ThroughputLatencyServer(processId, stateSize);
	}

	public ThroughputLatencyServer(int processId, int stateSize) {
		senders = new HashSet<>(1000);
		state = new byte[stateSize];
		for (int i = 0; i < stateSize; i++) {
			state[i] = (byte) i;
		}
		new ServiceReplica(processId, this, this);
	}

	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		return processRequest(msgCtx);
	}

	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		return processRequest(msgCtx);
	}

	private byte[] processRequest(MessageContext msgCtx) {
		numRequests++;
		senders.add(msgCtx.getSender());
		printMeasurement();
		return state;
	}

	private void printMeasurement() {
		long currentTime = System.nanoTime();
		long delay = currentTime - startTime;
		if (delay >= 2_000_000_000) {//more than 2 seconds
			measurementLogger.info("M-clients: {}", senders.size());
			measurementLogger.info("M-delta: {}", delay);
			measurementLogger.info("M-requests: {}", numRequests);

			//compute throughput
			double throughput = numRequests / (delay / 1_000_000_000.0);
			if (throughput > maxThroughput)
				maxThroughput = throughput;
			logger.info("(clients[#]|requests[#]|delta[ns]|throughput[ops/s], max[ops/s])>({}|{}|{}|{}|{})",
					senders.size(), numRequests, delay, throughput, maxThroughput);

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
		return state;
	}
}
