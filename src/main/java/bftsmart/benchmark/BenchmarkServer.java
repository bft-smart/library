package bftsmart.benchmark;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

public class BenchmarkServer extends DefaultSingleRecoverable {
	private final Logger measurementLogger = LoggerFactory.getLogger("measurement");
	private final Logger logger = LoggerFactory.getLogger("benchmarking");

	private byte[] state;
	private long lastTime;
	private long numRequests;
	private double maxThroughput;
	private final Set<Integer> senders;
	private final ServiceReplica serviceReplica;

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("USAGE: benchmark.BenchmarkServer <process id> <state size>");
			System.exit(-1);
		}
		int processId = Integer.parseInt(args[0]);
		int stateSize = Integer.parseInt(args[1]);
		new BenchmarkServer(processId, stateSize);
	}

	public BenchmarkServer(int processId, int stateSize) {
		this.senders = new HashSet<>(1000);
		this.state = new byte[stateSize];
		for (int i = 0; i < stateSize; i++) {
			state[i] = (byte) i;
		}
		serviceReplica = new ServiceReplica(processId, this, this);
	}

	@Override
	public void installSnapshot(byte[] state) {
		this.state = state;
	}

	@Override
	public byte[] getSnapshot() {
		return state == null ? new byte[0] : state;
	}

	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext messageContext) {
		return processRequest(messageContext);
	}

	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext messageContext) {
		return processRequest(messageContext);
	}

	private byte[] processRequest(MessageContext messageContext) {
		numRequests++;
		senders.add(messageContext.getSender());
		printReport();
		return state;
	}

	private void printReport() {
		long end = System.nanoTime();
		long delay = end - lastTime;
		if (delay >= 2_000_000_000) {
			measurementLogger.info("M-clients: {}", senders.size());
			measurementLogger.info("M-delta: {}", delay);
			measurementLogger.info("M-requests: {}", numRequests);

			//compute throughput
			double throughput = numRequests / (delay / 1_000_000_000.0);
			if (throughput > maxThroughput) {
				maxThroughput = throughput;
			}
			logger.info("clients[#]: {} | requests[#]: {} | delta[ns]:{} | throughput[ops/s]: {} (max: {})",
					senders.size(), numRequests, delay, throughput, maxThroughput);
			numRequests = 0;
			senders.clear();
			lastTime = end;
		}

	}
}
