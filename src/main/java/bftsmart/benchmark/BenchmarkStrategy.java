package bftsmart.benchmark;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import generic.DefaultMeasurements;
import generic.ResourcesMeasurements;
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

public class BenchmarkStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final String serverCommand;
	private final String clientCommand;
	private final String sarCommand;
	private WorkerHandler[] serverWorkers;
	private WorkerHandler[] clientWorkers;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final Map<Integer, WorkerHandler> measurementWorkers;
	private String storageFileNamePrefix;
	private CountDownLatch workersReadyCounter;
	private CountDownLatch measurementDeliveredCounter;

	public BenchmarkStrategy() {
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.measurementWorkers = new HashMap<>();
		String initialCommand = "java -Xmx28g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.serverCommand = initialCommand + "bftsmart.benchmark.BenchmarkServer ";
		this.clientCommand = initialCommand + "bftsmart.benchmark.BenchmarkClient ";
		this.sarCommand = "sar -u -r -n DEV 1";
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workers, Properties benchmarkParameters) {
		logger.info("Starting throughput-latency benchmark strategy");
		long startTime = System.currentTimeMillis();
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String hostsFile = benchmarkParameters.getProperty("experiment.hosts.file");
		String[] clientsPerRoundTokens = benchmarkParameters.getProperty("experiment.clients_per_round").split(" ");
		boolean measureResources = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.measure_resources"));
		boolean isWrite = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.is_write"));
		boolean useHashedResponse = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.use_hashed_response"));
		int requestDataSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.request_data_size"));
		int responseDataSize = Integer.parseInt(benchmarkParameters.getProperty("experiment.response_data_size"));
		int nRequests = 10_000_000;
		int maxClientsPerProcess = 30;
		int sleepBetweenRounds = 30;

		int[] clientsPerRound = new int[clientsPerRoundTokens.length];
		for (int i = 0; i < clientsPerRoundTokens.length; i++) {
			clientsPerRound[i] = Integer.parseInt(clientsPerRoundTokens[i]);
		}

		int nRounds = clientsPerRound.length;
		int nServerWorkers = 3 * f + 1;
		int nClientWorkers = workers.length - nServerWorkers;

		//Separate workers
		serverWorkers = new WorkerHandler[nServerWorkers];
		clientWorkers = new WorkerHandler[nClientWorkers];
		System.arraycopy(workers, 0, serverWorkers, 0, nServerWorkers);
		System.arraycopy(workers, nServerWorkers, clientWorkers, 0, nClientWorkers);

		//Client workers in ascending order to use always the same client for measurements
		Arrays.sort(clientWorkers, (o1, o2) -> -Integer.compare(o1.getWorkerId(), o2.getWorkerId()));
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		Arrays.stream(clientWorkers).forEach(w -> clientWorkersIds.add(w.getWorkerId()));

		logger.info("============ Strategy Parameters ============");
		printWorkersInfo();
		logger.info("f: {}", f);
		logger.info("Hosts file: {}", hostsFile);
		logger.info("Clients per round: {}", Arrays.toString(clientsPerRound));
		logger.info("Measure resources: {}", measureResources);
		logger.info("Is write: {}", isWrite);
		logger.info("Use hashed response: {}", useHashedResponse);
		logger.info("Request data size: {} bytes", requestDataSize);
		logger.info("Response data size: {} bytes", responseDataSize);

		//Setup workers
		if (hostsFile != null) {
			logger.info("Setting up workers...");
			String hosts = loadHosts(hostsFile);
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
				storageFileNamePrefix = String.format("f_%d_request_%d_bytes_response_%d_bytes_op_%s_response_%s_round_%d_",
						f, requestDataSize, responseDataSize, isWrite ? "write" : "read",
						useHashedResponse ? "hashed" : "full", nClients);

				//Distribute clients among workers
				int[] clientsPerWorker = distributeClientsPerWorkers(nClientWorkers, nClients);
				String vector = Arrays.toString(clientsPerWorker);
				int total = Arrays.stream(clientsPerWorker).sum();
				logger.info("Clients per worker: {} -> Total: {}", vector, total);

				//Start servers
				startServers(serverWorkers, responseDataSize);

				//Start clients
				startClient(clientWorkers, clientsPerWorker, isWrite, useHashedResponse, requestDataSize, nRequests,
						maxClientsPerProcess);

				//Start resource measurement
				if (measureResources) {
					int nServerResourceMeasurementWorkers = serverWorkers.length > 1 ? 2 : 1;
					int nClientResourceMeasurementWorkers = clientWorkers.length > 1 ? 2 : 1;
					nClientResourceMeasurementWorkers = Math.min(nClientResourceMeasurementWorkers,
							clientsPerWorker.length);// this is to account for 1 client
					startResourceMeasurements(nServerResourceMeasurementWorkers, nClientResourceMeasurementWorkers);
				}

				//Wait for system to stabilize
				logger.info("Waiting 15s...");
				sleepSeconds(15);

				//Get measurements
				getMeasurements(measureResources);

				//Stop processes
				Arrays.stream(workers).forEach(WorkerHandler::stopWorker);

				if (round == nRounds) {
					break;
				}

				//Wait between round
				logger.info("Waiting {}s before new round", sleepBetweenRounds);
				sleepSeconds(sleepBetweenRounds);
				round++;
			} catch (InterruptedException e) {
				break;
			} finally {
				lock.unlock();
			}
		}

		long endTime = System.currentTimeMillis();
		logger.info("Strategy execution duration: {}s", (endTime - startTime) / 1000);
	}

	private void startResourceMeasurements(int nServerResourceMeasurementWorkers,
										   int nClientResourceMeasurementWorkers) throws InterruptedException {
		WorkerHandler[] resourceMeasurementWorkers =
				new WorkerHandler[nServerResourceMeasurementWorkers + nClientResourceMeasurementWorkers];
		System.arraycopy(serverWorkers, 0, resourceMeasurementWorkers, 0, nServerResourceMeasurementWorkers);
		System.arraycopy(clientWorkers, 0, resourceMeasurementWorkers, nServerResourceMeasurementWorkers,
				nClientResourceMeasurementWorkers);

		logger.info("Starting resource measurements...");
		workersReadyCounter = new CountDownLatch(resourceMeasurementWorkers.length);
		for (WorkerHandler worker : resourceMeasurementWorkers) {
			measurementWorkers.put(worker.getWorkerId(), worker);
			ProcessInformation[] commands = {
					new ProcessInformation(sarCommand, ".")
			};
			worker.startWorker(0, commands, this);
		}
		workersReadyCounter.await();
	}

	private void startClient(WorkerHandler[] clientWorkers, int[] clientsPerWorker, boolean isWrite,
							 boolean useHashedResponse, int dataSize, int nRequests, int maxClientsPerProcess) throws InterruptedException {
		logger.info("Starting clients...");
		workersReadyCounter = new CountDownLatch(clientsPerWorker.length);
		int initialClientId = 100000;
		measurementWorkers.put(clientWorkers[0].getWorkerId(), clientWorkers[0]);

		for (int i = 0; i < clientsPerWorker.length; i++) {
			WorkerHandler clientWorker = clientWorkers[i];
			int totalClientsPerWorker = clientsPerWorker[i];
			int nProcesses = totalClientsPerWorker / maxClientsPerProcess
					+ (totalClientsPerWorker % maxClientsPerProcess == 0 ? 0 : 1);
			ProcessInformation[] commandInfo = new ProcessInformation[nProcesses];
			boolean isMeasurementWorker = i == 0;// First client is measurement client

			for (int j = 0; j < nProcesses; j++) {
				int clientsPerProcess = Math.min(totalClientsPerWorker, maxClientsPerProcess);
				String command = clientCommand + initialClientId + " " + clientsPerProcess
						+ " " + nRequests + " " + dataSize + " " + isWrite + " " + useHashedResponse
						+ " " + isMeasurementWorker;
				commandInfo[j] = new ProcessInformation(command, ".");
				totalClientsPerWorker -= clientsPerProcess;
				initialClientId += clientsPerProcess;
			}

			clientWorker.startWorker(0, commandInfo, this);
		}

		workersReadyCounter.await();
	}

	private void startServers(WorkerHandler[] serverWorkers, int dataSize) throws InterruptedException {
		logger.info("Starting servers...");
		workersReadyCounter = new CountDownLatch(serverWorkers.length);
		measurementWorkers.put(serverWorkers[0].getWorkerId(), serverWorkers[0]);

		for (int i = 0; i < serverWorkers.length; i++) {
			WorkerHandler serverWorker = serverWorkers[i];
			logger.debug("Using server worker {}", serverWorker.getWorkerId());
			String command = serverCommand + " " + i + " " + dataSize;
			ProcessInformation[] commandInfo = {
					new ProcessInformation(command, ".")
			};
			serverWorker.startWorker(0, commandInfo, this);
			sleepSeconds(2);
		}

		workersReadyCounter.await();
	}

	private void getMeasurements(boolean measureResources) throws InterruptedException {
		//Start measurements
		logger.info("Getting measurements...");
		measurementWorkers.values().forEach(WorkerHandler::startProcessing);

		//Wait for measurements
		logger.info("Measuring during {}s", 60 * 2);
		sleepSeconds(60 * 2);

		//Stop measurements
		measurementWorkers.values().forEach(WorkerHandler::stopProcessing);

		//Get measurement results
		int nMeasurements;
		if (measureResources) {
			//servers: 2 + 1
			//clients: 2 + 1
			nMeasurements = measurementWorkers.size() + 2;
		} else {
			nMeasurements = 2;
		}

		logger.debug("Getting {} measurements from {} workers...", nMeasurements, measurementWorkers.size());
		measurementDeliveredCounter = new CountDownLatch(nMeasurements);

		measurementWorkers.values().forEach(WorkerHandler::requestProcessingResult);

		measurementDeliveredCounter.await();
	}

	@Override
	public synchronized void onResult(int workerId, IProcessingResult processingResult) {
		if (!measurementWorkers.containsKey(workerId)) {
			logger.warn("Received measurements results from unused worker {}", workerId);
			return;
		}
		if (processingResult instanceof DefaultMeasurements	&& workerId == serverWorkers[0].getWorkerId()) {
			logger.debug("Received leader server performance results from worker {}", workerId);
			DefaultMeasurements measurements = (DefaultMeasurements) processingResult;
			processServerMeasurements(measurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof DefaultMeasurements && workerId == clientWorkers[0].getWorkerId()) {
			logger.debug("Received measurement client performance results from worker {}", workerId);
			DefaultMeasurements measurements = (DefaultMeasurements) processingResult;
			processClientMeasurements(measurements);
			measurementDeliveredCounter.countDown();
		} else if (processingResult instanceof ResourcesMeasurements) {
			String tag = null;
			if (workerId == serverWorkers[0].getWorkerId()) {
				logger.debug("Received leader server resources measurements from worker {}", workerId);
				tag = "primary_server";
			} else if (serverWorkers.length > 1 && workerId == serverWorkers[1].getWorkerId()) {
				logger.debug("Received follower server resources measurements from worker {}", workerId);
				tag = "secondary_server";
			} else if (workerId == clientWorkers[0].getWorkerId()) {
				logger.debug("Received measurement client resources measurements from worker {}", workerId);
				tag = "primary_client";
			} else if (clientWorkers.length > 1 && workerId == clientWorkers[1].getWorkerId()) {
				logger.debug("Received load client resources measurements from worker {}", workerId);
				tag = "secondary_client";
			}
			if (tag != null) {
				ResourcesMeasurements measurements = (ResourcesMeasurements) processingResult;
				processResourcesMeasurements(measurements, tag);
				measurementDeliveredCounter.countDown();
			} else {
				logger.warn("Received resources measurements from unused worker {}", workerId);
			}
		}
	}

	private void processServerMeasurements(DefaultMeasurements serverMeasurements) {
		saveServerMeasurements(serverMeasurements.getMeasurements());

		long[] clients = serverMeasurements.getMeasurements("clients");
		long[] delta = serverMeasurements.getMeasurements("delta");
		long[] nRequests = serverMeasurements.getMeasurements("requests");
		long[] executeUnorderedLatencies = serverMeasurements.getMeasurements("executeUnordered");
		long[] executeOrderedLatencies = serverMeasurements.getMeasurements("executeOrdered");

		int size = Math.min(clients.length, Math.min(delta.length, nRequests.length));

		long[] throughput = new long[size];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		for (int i = 0; i < size; i++) {
			minClients = Long.min(minClients, clients[i]);
			maxClients = Long.max(maxClients, clients[i]);
			throughput[i] = (long) (nRequests[i] / (delta[i] / 1_000_000_000.0));
		}
		Storage throughputStorage = new Storage(throughput);
		String sb = String.format("Server-side measurements [%d samples]:\n", throughput.length);
		if (executeUnorderedLatencies != null) {
			Storage st = new Storage(executeUnorderedLatencies);
			sb += String.format("\tExecuteUnordered latency[ms]: avg:%.3f dev:%.3f max: %d\n",
					st.getAverage(true) / 1_000_000.0, st.getDP(true) / 1_000_000.0,
					st.getMax(true) / 1_000_000);
		}
		if (executeOrderedLatencies != null) {
			Storage st = new Storage(executeOrderedLatencies);
			sb += String.format("\tExecuteOrdered latency[ms]: avg:%.3f dev:%.3f max: %d\n",
					st.getAverage(true) / 1_000_000.0, st.getDP(true) / 1_000_000.0,
					st.getMax(true) / 1_000_000);
		}
		sb += String.format("\tClients[#]: min:%d max:%d\n", minClients, maxClients);
		sb += String.format("\tThroughput [ops/s]: avg:%.3f dev:%.3f max: %d",
						throughputStorage.getAverage(true), throughputStorage.getDP(true),
						throughputStorage.getMax(true));
		logger.info(sb);
	}

	private void processClientMeasurements(DefaultMeasurements clientMeasurements) {
		saveClientMeasurements(clientMeasurements.getMeasurements());

		long[] globalLatencies = clientMeasurements.getMeasurements("global");
		Storage st = new Storage(globalLatencies);
		String sb = String.format("Client-side measurements [%d samples]:\n", globalLatencies.length) +
				String.format("\tAccess latency[ms]: avg:%.3f dev:%.3f max: %d",
						st.getAverage(true) / 1_000_000.0, st.getDP(true) / 1_000_000.0,
						st.getMax(true) / 1_000_000);
		logger.info(sb);
	}

	private void saveServerMeasurements(Map<String, long[]> measurements) {
		String fileName = storageFileNamePrefix + "server_global.csv";
		String header = "";
		PrimitiveIterator.OfLong[] iterators = new PrimitiveIterator.OfLong[measurements.size()];
		int i = 0;

		if (measurements.containsKey("executeUnordered")) {
			header += ",executeUnordered[ns]";
			iterators[i++] = Arrays.stream(measurements.get("executeUnordered")).iterator();
		}

		if (measurements.containsKey("executeOrdered")) {
			header += ",executeOrdered[ns]";
			iterators[i++] = Arrays.stream(measurements.get("executeOrdered")).iterator();
		}

		header += ",clients[#]";
		iterators[i++] = Arrays.stream(measurements.get("clients")).iterator();

		header += ",delta[ns]";
		iterators[i++] = Arrays.stream(measurements.get("delta")).iterator();

		header += ",requests[#]";
		iterators[i] = Arrays.stream(measurements.get("requests")).iterator();

		saveGlobalMeasurements(fileName, header.substring(1), iterators);
	}

	private void saveClientMeasurements(Map<String, long[]> measurements) {
		String fileName = storageFileNamePrefix + "client_global.csv";
		String header = "";
		PrimitiveIterator.OfLong[] iterators = new PrimitiveIterator.OfLong[measurements.size()];

		header += "global[ns]";
		iterators[0] = Arrays.stream(measurements.get("global")).iterator();

		saveGlobalMeasurements(fileName, header, iterators);
	}

	private void saveGlobalMeasurements(String fileName, String header, PrimitiveIterator.OfLong[] dataIterators) {
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(
				Files.newOutputStream(Paths.get(fileName))))) {
			resultFile.write(header + "\n");
			boolean hasData = true;
			while(true) {
				StringBuilder sb = new StringBuilder();
				for (PrimitiveIterator.OfLong iterator : dataIterators) {
					if (iterator.hasNext()) {
						sb.append(iterator.next());
						sb.append(",");
					} else {
						hasData = false;
						break;
					}
				}
				if (!hasData) {
					break;
				}
				sb.deleteCharAt(sb.length() - 1);
				resultFile.write(sb + "\n");
			}

			resultFile.flush();
		} catch (IOException e) {
			logger.error("Failed to save client measurements", e);
		}
	}

	private void processResourcesMeasurements(ResourcesMeasurements resourcesMeasurements, String tag) {
		long[] cpu = resourcesMeasurements.getCpu();
		long[] mem = resourcesMeasurements.getMemory();
		long[][] netReceived = resourcesMeasurements.getNetReceived();
		long[][] netTransmitted = resourcesMeasurements.getNetTransmitted();

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
					if (i < datum.length) {
						sb.append(String.format("%.2f", datum[i] / 100.0));
					}
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

	@Override
	public synchronized void onReady(int workerId) {
		logger.debug("Worker {} is ready", workerId);
		workersReadyCounter.countDown();
	}

	@Override
	public synchronized void onEnded(int workerId) {

	}

	@Override
	public synchronized void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			logger.error("Error in server worker {}: {}", workerId, errorMessage);
		} else if (clientWorkersIds.contains(workerId)) {
			logger.error("Error in client worker {}: {}", workerId, errorMessage);
		} else {
			logger.error("Error in unused worker {}: {}", workerId, errorMessage);
		}
	}

	private int[] distributeClientsPerWorkers(int nClientWorkers, int nClients) {
		if (nClients == 1 || nClientWorkers == 1) {
			return new int[]{nClients};
		}

		nClients--; //Subtract the measurement client
		nClientWorkers--; //Subtract the measurement client

		if (nClients <= nClientWorkers) {
			int[] distribution = new int[1 + nClients];
			Arrays.fill(distribution, 1);
			return distribution;
		}

		int[] distribution = new int[1 + nClientWorkers];
		int nClientsPerWorker = nClients / nClientWorkers;
		Arrays.fill(distribution, nClientsPerWorker);
		distribution[0] = 1;//Measurement client
		int remainingClients = nClients % nClientWorkers;
		for (int i = 1; i <= remainingClients; i++) {
			distribution[i]++;
		}
		return distribution;
	}

	private void printWorkersInfo() {
		StringBuilder sb = new StringBuilder();
		for (WorkerHandler serverWorker : serverWorkers) {
			sb.append(serverWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Server workers[{}]: {}", serverWorkers.length, sb);

		sb = new StringBuilder();
		for (WorkerHandler clientWorker : clientWorkers) {
			sb.append(clientWorker.getWorkerId());
			sb.append(" ");
		}
		logger.info("Client workers[{}]: {}", clientWorkers.length, sb);
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
