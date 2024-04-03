package bftsmart.benchmark;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Storage;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ThroughputLatencyBenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final String serverCommand;
	private final String clientCommand;
	private final String sarCommand;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private CountDownLatch measurementDeliveredCounter;
	private WorkerHandler[] serverWorkers;
	private WorkerHandler[] clientWorkers;
	private final Map<Integer, WorkerHandler> measurementWorkers;
	private int dataSize;
	private boolean isWrite;
	private ArrayList<Integer> numMaxRealClients;
	private ArrayList<Double> avgLatency;
	private ArrayList<Double> latencyDev;
	private ArrayList<Double> avgThroughput;
	private ArrayList<Double> throughputDev;
	private ArrayList<Double> maxLatency;
	private ArrayList<Double> maxThroughput;
	private boolean measureResources;
	private String storageFileNamePrefix;
	private int f;
	private boolean useHashedResponse;

	public ThroughputLatencyBenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		String initialCommand = "java -Xmx28g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.serverCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyServer ";
		this.clientCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
	}
	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		logger.info("Starting throughput-latency benchmark strategy");
		long startTime = System.currentTimeMillis();
		f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String[] tokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		dataSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.data_size"));
		isWrite = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.is_write"));
		useHashedResponse = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.use_hashed_response"));
		String hostFile = benchmarkParameters.getProperty("experiment.hosts.file");
		measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.measure_resources"));

		int nServerWorkers = 3 * f + 1;
		int nClientWorkers = workers.length - nServerWorkers;
		int maxClientsPerProcess = 30;
		int nRequests = 10_000_000;
		int sleepBetweenRounds = 10;
		int[] clientsPerRound = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(tokens[i]);
		}
		int nRounds = clientsPerRound.length;
		numMaxRealClients = new ArrayList<>(nRounds);
		avgLatency = new ArrayList<>(nRounds);
		latencyDev = new ArrayList<>(nRounds);
		avgThroughput = new ArrayList<>(nRounds);
		throughputDev = new ArrayList<>(nRounds);
		maxLatency = new ArrayList<>(nRounds);
		maxThroughput = new ArrayList<>(nRounds);

		//Separate workers
		serverWorkers = new WorkerHandler[nServerWorkers];
		clientWorkers = new WorkerHandler[nClientWorkers];
		System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
		for (int i = 0, j = workers.length - 1; i < nClientWorkers; i++, j--) {
			clientWorkers[i] = workers[j];
		}
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

		printWorkersInfo();

		//Setup workers
		if (hostFile != null) {
			logger.info("Setting up workers...");
			String hosts = loadHosts(hostFile);
			if (hosts == null)
				return;
			String setupInformation = String.format("%b\t%d\t%s", true, f, hosts);
			Arrays.stream(workers).forEach(w -> w.setupWorker(setupInformation));
		}

		int round = 1;
		while (true) {
			try {
				lock.lock();
				logger.info("============ Round {} out of {} ============", round, nRounds);
				int nClients = clientsPerRound[round - 1];
				measurementWorkers.clear();
				storageFileNamePrefix = String.format("f_%d_%d_bytes_%s_round_%d_", f, dataSize,
						isWrite ? "write" : "read", nClients);

				//Distribute clients per workers
				int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients, maxClientsPerProcess);
				String vector = Arrays.toString(clientsPerWorker);
				int total = Arrays.stream(clientsPerWorker).sum();
				logger.info("Clients per worker: {} -> Total: {}", vector, total);

				//Start servers
				startServers(dataSize, nServerWorkers, serverWorkers);

				//Start clients
				startClients(nServerWorkers, maxClientsPerProcess, nRequests, dataSize, isWrite,
						clientWorkers, clientsPerWorker);

				//Wait for system to stabilize
				logger.info("Waiting 10s...");
				sleepSeconds(10);

				//Get measurements
				getMeasurements();

				//Stop processes
				Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

				round++;
				if (round > nRounds) {
					storeResumedMeasurements(numMaxRealClients, avgLatency, latencyDev, avgThroughput,
							throughputDev, maxLatency, maxThroughput);
					break;
				}

				//Wait between round
				logger.info("Waiting {}s before new round", sleepBetweenRounds);
				sleepSeconds(sleepBetweenRounds);
			} catch (InterruptedException e) {
				break;
			} finally {
				lock.unlock();
			}
		}
		long endTime = System.currentTimeMillis();
		logger.info("Strategy execution duration: {}s", (endTime - startTime) / 1000);
	}

	private void printWorkersInfo() {
		StringBuilder sb = new StringBuilder();
		for (WorkerHandler serverWorker : serverWorkers) {
			sb.append(serverWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Server workers: {}", sb);

		sb = new StringBuilder();
		for (WorkerHandler clientWorker : clientWorkers) {
			sb.append(clientWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Client workers: {}", sb);
	}

	private String loadHosts(String hostFile) {
		try (BufferedReader in = new BufferedReader(new FileReader(hostFile))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
			sb.deleteCharAt(sb.length() - 1);
			return sb.toString();
		} catch (IOException e) {
			logger.error("Failed to load hosts file", e);
			return null;
		}
	}

	private void getMeasurements() throws InterruptedException {
		//Start measurements
		logger.debug("Starting measurements...");
		measurementWorkers.values().forEach(WorkerHandler::startProcessing);

		//Wait for measurements
		logger.info("Measuring during 120s");
		sleepSeconds(120);

		//Stop measurements
		measurementWorkers.values().forEach(WorkerHandler::stopProcessing);

		//Get measurement results
		int nMeasurements;
		if (measureResources) {
			if (measurementWorkers.size() == 3) {
				nMeasurements = 5;
			} else {
				nMeasurements = 6;
			}
		} else {
			nMeasurements = 2;
		}
		logger.debug("Getting {} measurements from {} workers...", nMeasurements, measurementWorkers.size());
		measurementDeliveredCounter = new CountDownLatch(nMeasurements);

		measurementWorkers.values().forEach(WorkerHandler::requestProcessingResult);

		measurementDeliveredCounter.await();
	}

	private void startClients(int nServerWorkers, int maxClientsPerProcess, int nRequests,
							  int dataSize, boolean isWrite, WorkerHandler[] clientWorkers,
							  int[] clientsPerWorker) throws InterruptedException {
		logger.info("Starting clients...");
		clientsReadyCounter = new CountDownLatch(clientsPerWorker.length);
		int clientInitialId = nServerWorkers + 1000;
		measurementWorkers.put(clientWorkers[0].getWorkerId(), clientWorkers[0]);
		if (measureResources && clientsPerWorker.length > 1)
			measurementWorkers.put(clientWorkers[1].getWorkerId(), clientWorkers[1]);

		for (int i = 0; i < clientsPerWorker.length && i < clientWorkers.length; i++) {
			int totalClientsPerWorker = clientsPerWorker[i];
			int nProcesses = totalClientsPerWorker / maxClientsPerProcess
					+ (totalClientsPerWorker % maxClientsPerProcess == 0 ? 0 : 1);
			int nCommands = nProcesses + (measureResources && i < 2 ? 1 : 0);
			ProcessInformation[] commands = new ProcessInformation[nCommands];
			boolean isMeasurementWorker = i == 0;// First client is measurement client

			for (int j = 0; j < nProcesses; j++) {
				int clientsPerProcess = Math.min(totalClientsPerWorker, maxClientsPerProcess);
				String command = clientCommand + clientInitialId + " " + clientsPerProcess
						+ " " + nRequests + " " + dataSize + " " + isWrite + " " + useHashedResponse + " "
						+ isMeasurementWorker;
				commands[j] = new ProcessInformation(command, ".");
				totalClientsPerWorker -= clientsPerProcess;
				clientInitialId += clientsPerProcess;
			}
			if (measureResources && i < 2) {// Measure resources of measurement and a load client
				commands[nProcesses] = new ProcessInformation(sarCommand, ".");
			}
			clientWorkers[i].startWorker(50, commands, this);
		}
		clientsReadyCounter.await();
	}

	private void startServers(int dataSize, int nServerWorkers,
							  WorkerHandler[] serverWorkers) throws InterruptedException {
		logger.info("Starting servers...");
		serversReadyCounter = new CountDownLatch(nServerWorkers);
		measurementWorkers.put(serverWorkers[0].getWorkerId(), serverWorkers[0]);
		if (measureResources)
			measurementWorkers.put(serverWorkers[1].getWorkerId(), serverWorkers[1]);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + i + " " + dataSize;
			int nCommands = measureResources && i < 2 ? 2 : 1;
			ProcessInformation[] commands = new ProcessInformation[nCommands];
			commands[0] = new ProcessInformation(command, ".");
			if (measureResources && i < 2) {// Measure resources of leader and a follower server
				commands[1] = new ProcessInformation(sarCommand, ".");
			}
			serverWorkers[i].startWorker(0, commands, this);
			sleepSeconds(2);
		}
		serversReadyCounter.await();
	}

	private int[] distributeClientsPerWorkers(int nClientWorkers, int nClients, int maxClientsPerProcess) {
		if (nClients == 1) {
			return new int[]{1};
		}
		if (nClientWorkers < 2) {
			return new int[]{nClients};
		}
		nClients--;//remove measurement client
		if (nClients <= maxClientsPerProcess) {
			return new int[]{1, nClients};
		}
		nClientWorkers--; //for measurement client
		int nWorkersToUse = Math.min(nClientWorkers, (int)Math.ceil((double) nClients / maxClientsPerProcess));
		int[] distribution = new int[nWorkersToUse + 1];//one for measurement worker
		Arrays.fill(distribution, nClients / nWorkersToUse);
		distribution[0] = 1;
		nClients -= nWorkersToUse * (nClients / nWorkersToUse);
		int i = 1;
		while (nClients > 0) {
			nClients--;
			distribution[i]++;
			i = (i + 1) % distribution.length;
			if (i == 0) {
				i++;
			}
		}

		return distribution;
	}

	@Override
	public void onReady(int workerId) {
		if (serverWorkersIds.contains(workerId)) {
			serversReadyCounter.countDown();
		} else if (clientWorkersIds.contains(workerId)) {
			clientsReadyCounter.countDown();
		}
	}

	@Override
	public void onEnded(int workerId) {

	}

	@Override
	public void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			logger.error("Error in server worker {}: {}", workerId, errorMessage);
		} else if (clientWorkersIds.contains(workerId)) {
			logger.error("Error in client worker {}: {}", workerId, errorMessage);
		} else {
			logger.error("Error in unused worker {}: {}", workerId, errorMessage);
		}
	}

	@Override
	public synchronized void onResult(int workerId, IProcessingResult processingResult) {
		Measurement measurement = (Measurement) processingResult;
		long[][] measurements = measurement.getMeasurements();
		logger.debug("Received {} measurements from worker {}", measurements.length, workerId);
		if (!measurementWorkers.containsKey(workerId)) {
			logger.warn("Received measurements results from unused worker");
			return;
		}

		if (serverWorkersIds.contains(workerId)) {
			if (serverWorkers[0].getWorkerId() == workerId) { //leader server
				if (measurements.length == 3) {
					logger.debug("Received leader server throughput results");
					processServerMeasurementResults(measurements[0], measurements[1], measurements[2]);
					measurementDeliveredCounter.countDown();
				} else {
					logger.debug("Received leader server resources usage results");
					processResourcesMeasurements(measurements, "leader_server");
					measurementDeliveredCounter.countDown();
				}
			} else if (serverWorkers[1].getWorkerId() == workerId) { //follower server
				if (measurements.length > 3) {
					logger.debug("Received follower server resources usage results");
					processResourcesMeasurements(measurements, "follower_server");
					measurementDeliveredCounter.countDown();
				}
			}
		} else if (clientWorkersIds.contains(workerId)) {
			if (clientWorkers[0].getWorkerId() == workerId) { //measurement client
				if (measurements.length == 1) {
					logger.debug("Received measurement client latency results");
					processClientMeasurementResults(measurements[0]);
					measurementDeliveredCounter.countDown();
				} else {
					logger.debug("Received measurement client resources usage results");
					processResourcesMeasurements(measurements, "measurement_client");
					measurementDeliveredCounter.countDown();
				}
			} else if (clientWorkers[1].getWorkerId() == workerId) { //load client
				if (measurements.length > 1) {
					logger.debug("Received load client resources usage results");
					processResourcesMeasurements(measurements, "load_client");
					measurementDeliveredCounter.countDown();
				}
			}
		} else {
			logger.warn("Received unused worker measurement results");
		}
	}

	private void storeResumedMeasurements(ArrayList<Integer> numMaxRealClients, ArrayList<Double> avgLatency, ArrayList<Double> latencyDev,
										  ArrayList<Double> avgThroughput, ArrayList<Double> throughputDev, ArrayList<Double> maxLatency,
										  ArrayList<Double> maxThroughput) {
		String fileName = "measurements_f_" + f + "_"  + dataSize + "_bytes_" + (isWrite ? "write" : "read") +".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			resultFile.write("clients(#),avgLatency(ns),latencyDev(ns),avgThroughput(ops/s)," +
					"throughputDev(ops/s),maxLatency(ns),maxThroughput(ops/s)\n");
			for (int i = 0; i < numMaxRealClients.size(); i++) {
				int clients = numMaxRealClients.get(i);
				double aLat = avgLatency.get(i);
				double dLat = latencyDev.get(i);
				double aThr = avgThroughput.get(i);
				double dThr = throughputDev.get(i);
				double mLat = maxLatency.get(i);
				double mThr = maxThroughput.get(i);
				resultFile.write(String.format("%d,%f,%f,%f,%f,%f,%f\n", clients, aLat, dLat, aThr, dThr, mLat, mThr));
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing summarized results", e);
		}
	}

	private void processClientMeasurementResults(long[] latencies) {
		saveClientMeasurements(latencies);
		Storage st = new Storage(latencies);
		logger.info("Client Measurement[ms] - avg:{} dev:{} max:{} [{} samples]", st.getAverage(true) / 1_000_000.0,
				st.getDP(true) / 1_000_000.0, st.getMax(true) / 1_000_000.0, latencies.length);
		avgLatency.add(st.getAverage(true));
		latencyDev.add(st.getDP(true));
		maxLatency.add((double) st.getMax(true));
	}

	private void processResourcesMeasurements(long[][] data, String tag) {
		long[] cpu = data[0];
		long[] mem = data[1];
		int nInterfaces = (data.length - 2) / 2;
		long[][] netReceived = new long[nInterfaces][];
		long[][] netTransmitted = new long[nInterfaces][];
		for (int i = 0; i < nInterfaces; i++) {
			netReceived[i] = data[2 + 2 * i];
			netTransmitted[i] = data[2 + 2 * i + 1];
		}
		String fileName = storageFileNamePrefix + "cpu_" + tag + ".csv";
		saveResourcesMeasurements(fileName, cpu);

		fileName = storageFileNamePrefix + "mem_" + tag + ".csv";
		saveResourcesMeasurements(fileName, mem);

		fileName = storageFileNamePrefix + "net_received_" + tag + ".csv";
		saveResourcesMeasurements(fileName, netReceived);

		fileName = storageFileNamePrefix + "net_transmitted_" + tag + ".csv";
		saveResourcesMeasurements(fileName, netTransmitted);
	}

	private void saveResourcesMeasurements(String fileName, long[]... data) {
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			int size = data[0].length;
			int i = 0;
			while (i < size) {
				StringBuilder sb = new StringBuilder();
				for (long[] datum : data) {
					sb.append(String.format("%.2f", datum[i] / 100.0));
					sb.append(",");
				}
				sb.deleteCharAt(sb.length() - 1);
				resultFile.write(sb + "\n");
				i++;
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing resources measurements results", e);
		}
	}

	private void processServerMeasurementResults(long[] clients, long[] nRequests, long[] delta) {
		saveServerMeasurements(clients, nRequests, delta);
		long[] th = new long[clients.length];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		int size = clients.length;
		for (int i = 0; i < size; i++) {
			minClients = Long.min(minClients, clients[i]);
			maxClients = Long.max(maxClients, clients[i]);
			th[i] = (long) (nRequests[i] / (delta[i] / 1_000_000_000.0));
		}
		Storage st = new Storage(th);
		logger.info("Server Measurement[ops/s] - avg:{} dev:{} max:{} | minClients:{} maxClients:{} [{} samples]",
				st.getAverage(true), st.getDP(true), st.getMax(true), minClients, maxClients,
				clients.length);
		avgThroughput.add(st.getAverage(true));
		numMaxRealClients.add((int) maxClients);
		throughputDev.add(st.getDP(true));
		maxThroughput.add((double) st.getMax(true));
	}

	public void saveServerMeasurements(long[] clients, long[] nRequests, long[] delta) {
		String fileName = storageFileNamePrefix + "server_global.csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			int size = clients.length;
			resultFile.write("clients(#),requests(#),delta(ns)\n");
			for (int i = 0; i < size; i++) {
				resultFile.write(String.format("%d,%d,%d\n", clients[i], nRequests[i], delta[i]));
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing server results", e);
		}
	}

	public void saveClientMeasurements(long[] latencies) {
		String fileName = storageFileNamePrefix + "client_global.csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			resultFile.write("latency(ns)\n");
			for (long l : latencies) {
				resultFile.write(String.format("%d\n", l));
			}
			resultFile.flush();
		} catch (IOException e) {
			logger.error("Error while storing client results", e);
		}
	}

	private void sleepSeconds(long duration) throws InterruptedException {
		lock.lock();
		ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
		scheduledExecutorService.schedule(() -> {
			lock.lock();
			sleepCondition.signal();
			lock.unlock();
		}, duration, TimeUnit.SECONDS);
		sleepCondition.await();
		scheduledExecutorService.shutdown();
		lock.unlock();
	}
}
