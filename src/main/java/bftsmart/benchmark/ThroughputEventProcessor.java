package bftsmart.benchmark;

import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

import java.util.LinkedList;

public class ThroughputEventProcessor implements IWorkerEventProcessor {
	private static final String SERVER_READY_PATTERN = "Replica state is up to date";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String MEASUREMENT_PATTERN = "M:";

	private boolean isReady;
	private boolean isServerWorker;
	private boolean doMeasurement;
	private LinkedList<String> rawMeasurements;

	public ThroughputEventProcessor() {
		this.rawMeasurements = new LinkedList<>();
	}

	@Override
	public void process(String line) {
		if (!isReady) {
			if (line.contains(SERVER_READY_PATTERN)) {
				isReady = true;
				isServerWorker = true;
			} else if (line.contains(CLIENT_READY_PATTERN)) {
				isReady = true;
			}
		}
		if (doMeasurement && line.contains(MEASUREMENT_PATTERN)) {
			rawMeasurements.add(line);
		}
	}

	@Override
	public void startProcessing() {
		System.out.println("Measuring");
		rawMeasurements.clear();
		doMeasurement = true;
	}

	@Override
	public void stopProcessing() {
		System.out.println("Not Measuring");
		doMeasurement = false;
	}

	@Override
	public IProcessingResult getProcessingResult() {
		if (isServerWorker) {
			return processServerMeasurements();
		} else {
			return processClientMeasurements();
		}
	}

	private IProcessingResult processClientMeasurements() {
		long[] latencies = new long[rawMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawMeasurements) {
			latencies[i++] = Long.parseLong(rawMeasurement.split(" ")[1]);
		}
		return new Measurement(latencies);
	}

	private IProcessingResult processServerMeasurements() {
		long[] clients = new long[rawMeasurements.size()];
		long[] numRequests = new long[rawMeasurements.size()];
		long[] delta = new long[rawMeasurements.size()];
		int i = 0;
		for (String rawMeasurement : rawMeasurements) {
			String token = rawMeasurement.split(">")[1];
			token = token.substring(1, token.length() - 1);
			String[] strValues = token.split("\\|");
			clients[i] = Long.parseLong(strValues[0]);
			numRequests[i] = Long.parseLong(strValues[1]);
			delta[i] = Long.parseLong(strValues[2]);
			i++;
		}
		return new Measurement(clients, numRequests, delta);
	}

	@Override
	public boolean isReady() {
		return isReady;
	}

	@Override
	public boolean ended() {
		return false;
	}
}
