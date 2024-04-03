package bftsmart.benchmark;

import worker.IProcessingResult;
import worker.IWorkerEventProcessor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ThroughputEventProcessor implements IWorkerEventProcessor {
	private static final String SERVER_READY_PATTERN = "Replica state is up to date";
	private static final String CLIENT_READY_PATTERN = "Executing experiment";
	private static final String MEASUREMENT_PATTERN = "M:";
	private static final String SAR_READY_PATTERN = "%";

	private boolean isReady;
	private boolean isServerWorker;
	private boolean doMeasurement;
	private final LinkedList<String> rawMeasurements;
	private final LinkedList<String[]> resourcesMeasurements;
	private final Pattern timePattern;

	public ThroughputEventProcessor() {
		this.rawMeasurements = new LinkedList<>();
		this.resourcesMeasurements = new LinkedList<>();
		String pattern = "^\\d{2}:\\d{2}:\\d{2}";
		this.timePattern = Pattern.compile(pattern);
	}

	@Override
	public void process(String line) {
		if (!isReady) {
			if (line.contains(SERVER_READY_PATTERN)) {
				isReady = true;
				isServerWorker = true;
			} else if (line.contains(CLIENT_READY_PATTERN)) {
				isReady = true;
			} else if (line.contains(SAR_READY_PATTERN)) {
				isReady = true;
			}
		}
		if (doMeasurement) {
			Matcher matcher = timePattern.matcher(line);
			if (line.contains(MEASUREMENT_PATTERN)) {
				rawMeasurements.add(line);
			} else if (matcher.find() && !line.contains("%")) {
				String[] tokens = line.split("\\s+");
				resourcesMeasurements.add(tokens);
			}
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
		if (!resourcesMeasurements.isEmpty())
			return processResourcesMeasurements();
		if (isServerWorker) {
			return processServerMeasurements();
		} else {
			return processClientMeasurements();
		}
	}

	private IProcessingResult processResourcesMeasurements() {
		LinkedList<Double> cpuMeasurements = new LinkedList<>();
		LinkedList<Long> memMeasurements = new LinkedList<>();
		Map<String, LinkedList<Double>> netReceivedMeasurements = new HashMap<>();
		Map<String, LinkedList<Double>> netTransmittedMeasurements = new HashMap<>();
		for (String[] tokens : resourcesMeasurements) {
			if (tokens.length <= 9) {
				double cpuUsage = Double.parseDouble(tokens[tokens.length - 6]);// %usr
				cpuMeasurements.add(cpuUsage);
			} else if (tokens.length <= 11) {
				String netInterface = tokens[tokens.length - 9];
				if (!netTransmittedMeasurements.containsKey(netInterface)) {
					netReceivedMeasurements.put(netInterface, new LinkedList<>());
					netTransmittedMeasurements.put(netInterface, new LinkedList<>());
				}
				double netReceived = Double.parseDouble(tokens[tokens.length - 6]);// rxkB/s
				double netTransmitted = Double.parseDouble(tokens[tokens.length - 5]);// txkB/s
				netReceivedMeasurements.get(netInterface).add(netReceived);
				netTransmittedMeasurements.get(netInterface).add(netTransmitted);
			} else {
				long memUsage = Long.parseLong(tokens[tokens.length - 9]);// kbmemused
				memMeasurements.add(memUsage);
			}
		}

		long[][] data = new long[netReceivedMeasurements.size() * 2 + 2][];
		int i = 0;
		data[i++] = doubleToLongArray(cpuMeasurements);
		data[i++] = longToLongArray(memMeasurements);
		for (String netInterface : netReceivedMeasurements.keySet()) {
			data[i++] = doubleToLongArray(netReceivedMeasurements.get(netInterface));
			data[i++] = doubleToLongArray(netTransmittedMeasurements.get(netInterface));
		}

		return new Measurement(data);
	}

	private static long[] doubleToLongArray(LinkedList<Double> list) {
		long[] array = new long[list.size()];
		int i = 0;
		for (double measurement : list) {
			array[i++] = (long) (measurement * 100);
		}
		return array;
	}

	private static long[] longToLongArray(LinkedList<Long> list) {
		long[] array = new long[list.size()];
		int i = 0;
		for (long measurement : list) {
			array[i++] = measurement * 100;
		}
		return array;
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
