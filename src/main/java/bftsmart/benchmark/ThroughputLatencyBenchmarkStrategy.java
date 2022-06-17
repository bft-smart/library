package bftsmart.benchmark;

import controller.IBenchmarkStrategy;
import demo.Measurement;
import master.IProcessingResult;
import master.client.ClientsMaster;
import master.message.Message;
import master.server.ServersMaster;
import pod.ProcessInfo;
import pod.WorkerCommands;
import util.Configuration;
import util.Storage;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author robin
 */
public class ThroughputLatencyBenchmarkStrategy implements IBenchmarkStrategy {
	private final Lock lock;
	private final Condition sleepCondition;
	private final int totalRounds;
	private int round;
	private final long sleepBetweenRounds;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private CountDownLatch measurementDelivered;

	private final String workingDirectory;
	private final String serverCommand;
	private final int numOfServers;

	private final String clientCommand;
	private final int numOfClients;
	private final boolean isWrite;
	private final int numRequests;
	private final int dataSize;
	private final int[] clients;
	private final int maxClientsPerWorker;

	private final int[] numMaxRealClients;
	private final double[] avgLatency;
	private final double[] latencyDev;
	private final double[] avgThroughput;
	private final double[] throughputDev;
	private final double[] maxLatency;
	private final double[] maxThroughput;


	public ThroughputLatencyBenchmarkStrategy() {
		Configuration configuration = Configuration.getInstance();
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.totalRounds = configuration.getClientsPerRound().length;
		this.numOfServers = configuration.getNumServerPods();
		this.numOfClients = configuration.getNumClientPods();
		this.numMaxRealClients = new int[totalRounds];
		this.avgLatency = new double[totalRounds];
		this.latencyDev = new double[totalRounds];
		this.avgThroughput = new double[totalRounds];
		this.throughputDev = new double[totalRounds];
		this.maxLatency = new double[totalRounds];
		this.maxThroughput = new double[totalRounds];
		this.sleepBetweenRounds = 10;
		this.isWrite = configuration.isWrite();
		this.numRequests = 10_000_000;
		this.dataSize = configuration.getDataSize();
		this.clients = configuration.getClientsPerRound();
		for (int i = 0; i < clients.length; i++) {
			clients[i]--;
		}
		this.maxClientsPerWorker = configuration.getMaxClientsPerWorker();
		this.workingDirectory = configuration.getWorkingDirectory();

		String initialCommand = "java -Xmx28g -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* ";

		this.serverCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyServer ";
		this.clientCommand = initialCommand + "bftsmart.benchmark.ThroughputLatencyClient ";
	}

	@Override
	public void executeBenchmark(ServersMaster serversMaster, long[] serverPodsIds, ClientsMaster clientsMaster,
								 long[] clientPodsIds) {
		round = 1;
		long measurementClientId = clientPodsIds[0];
		long measurementServerId = serverPodsIds[0];
		while (true) {
			try {
				lock.lock();
				System.out.println("=========== Round: " + round + " of " + totalRounds + " ===========");
				int[] clientsPerPod = computeClientsPerPod(clients[round - 1]);


				System.out.println("Starting servers");

				serversReadyCounter = new CountDownLatch(numOfServers);
				WorkerCommands[] serverCommands = new WorkerCommands[numOfServers];
				for (int i = 0; i < numOfServers; i++) {
					serverCommands[i] = new WorkerCommands(serverPodsIds[i],
							new ProcessInfo[]{
									new ProcessInfo(serverCommand + i, workingDirectory)
							});
				}
				serversMaster.startWorkers(0, serverCommands);
				serversReadyCounter.await();

				System.out.println("Starting clients");
				clientsReadyCounter = new CountDownLatch(1 + clientsPerPod.length);
				int clientId = numOfServers + 100;
				WorkerCommands[] clientCommands = new WorkerCommands[1 + clientsPerPod.length];
				clientCommands[0] = new WorkerCommands(
						measurementClientId, new ProcessInfo[]{
						new ProcessInfo(clientCommand + clientId + " 1 " + numRequests
								+ " " + dataSize + " " + isWrite + " true", workingDirectory)
				});
				clientId++;
				for (int i = 0; i < clientsPerPod.length; i++) {
					int totalClientsPerPod = clientsPerPod[i];
					int numOfWorkers = totalClientsPerPod / maxClientsPerWorker
							+ (totalClientsPerPod % maxClientsPerWorker == 0 ? 0 : 1);

					ProcessInfo[] processes = new ProcessInfo[numOfWorkers];
					for (int j = 0; j < numOfWorkers; j++) {
						int clientsPerWorker = Math.min(totalClientsPerPod, maxClientsPerWorker);
						String command = clientCommand + clientId + " " + clientsPerWorker + " "
								+ numRequests + " " + dataSize + " " + isWrite + " false";
						totalClientsPerPod -= clientsPerWorker;
						processes[j] = new ProcessInfo(command, workingDirectory);
						clientId += clientsPerWorker;
					}

					clientCommands[i + 1] = new WorkerCommands(clientPodsIds[i + 1], processes);
				}
				clientsMaster.startWorkers(50, clientCommands);

				clientsReadyCounter.await();

				System.out.println("Waiting 10s");
				sleepSeconds(10);

				System.out.println("Starting measurement");
				clientsMaster.startProcessing(measurementClientId);
				serversMaster.startProcessing(measurementServerId);

				System.out.println("Waiting 120s");
				sleepSeconds(120);

				System.out.println("Stopping server side measurement");
				clientsMaster.stopProcessing(measurementClientId);
				serversMaster.stopProcessing(measurementServerId);

				measurementDelivered = new CountDownLatch(2);
				System.out.println("Getting measurements");
				serversMaster.requestProcessingResult(measurementServerId);
				clientsMaster.requestProcessingResult(measurementClientId);

				System.out.println("Waiting for measurements");
				measurementDelivered.await();

				System.out.println("Stopping processes");
				serversMaster.stopWorkers();
				clientsMaster.stopWorkers();
				round++;
				if (round > totalRounds || numOfClients == 1) {
					storeResumedMeasurements(numMaxRealClients, avgLatency, latencyDev, avgThroughput, throughputDev, maxLatency, maxThroughput);
					break;
				}
				System.out.println("Waiting " + sleepBetweenRounds + "s");
				sleepSeconds(sleepBetweenRounds);
			} catch (InterruptedException e) {
				break;
			} finally {
				lock.unlock();
			}
		}
	}

	private int[] computeClientsPerPod(int totalClients) {
		if (totalClients == 0 || numOfClients == 1)
			return new int[0];

		int[] clientsPerPod = new int[numOfClients - 1];
		int perPod = totalClients / clientsPerPod.length;

		for (int i = 0; i < clientsPerPod.length; i++) {
			int temp = Math.min(perPod, totalClients);
			clientsPerPod[i] = temp;
			totalClients -= temp;
			if (totalClients <= 0)
				break;
		}
		int i = 0;
		while (totalClients > 0) {
			totalClients--;
			clientsPerPod[i]++;
			i = (i+1) % clientsPerPod.length;
		}

		String vector = Arrays.toString(clientsPerPod);
		int total = Arrays.stream(clientsPerPod).sum();
		System.out.println("Clients per pod: " + vector + " -> Total: " + total);

		return clientsPerPod;
	}

	@Override
	public void processServerReadyEvent(Message message) {
		serversReadyCounter.countDown();
	}

	private void storeResumedMeasurements(int[] numMaxRealClients, double[] avgLatency, double[] latencyDev,
										  double[] avgThroughput, double[] throughputDev, double[] maxLatency,
										  double[] maxThroughput) {
		String fileName = "measurements-" + dataSize + "bytes-" + (isWrite ? "write" : "read") +".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			resultFile.write("clients(#),avgLatency(ns),latencyDev(ns),avgThroughput(ops/s),throughputDev(ops/s),maxLatency(ns),maxThroughput(ops/s)\n");
			for (int i = 0; i < totalRounds; i++) {
				int clients = numMaxRealClients[i];
				double aLat = avgLatency[i];
				double dLat = latencyDev[i];
				double aThr = avgThroughput[i];
				double dThr = throughputDev[i];
				double mLat = maxLatency[i];
				double mThr = maxThroughput[i];
				resultFile.write(String.format("%d,%f,%f,%f,%f,%f,%f\n", clients, aLat, dLat, aThr, dThr, mLat, mThr));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void processClientReadyEvent(Message message) {
		clientsReadyCounter.countDown();
	}

	@Override
	public void deliverServerProcessingResult(IProcessingResult processingResult) {
		Measurement measurement = (Measurement) processingResult;
		System.out.println("Received server measurement result");
		saveServerMeasurements(round, measurement);
		long[] clients = measurement.getMeasurements()[0];
		long[] requests = measurement.getMeasurements()[1];
		long[] delta = measurement.getMeasurements()[2];
		long[] th = new long[clients.length];
		long minClients = Long.MAX_VALUE;
		long maxClients = Long.MIN_VALUE;
		int size = clients.length;
		for (int i = 0; i < size; i++) {
			minClients = Long.min(minClients, clients[i]);
			maxClients = Long.max(maxClients, clients[i]);
			th[i] = (long) (requests[i] / (delta[i] / 1_000_000_000.0));
		}
		Storage st = new Storage(th);
		System.out.printf("Server Measurement[ops/s] - avg:%f dev:%f max:%d | minClients:%d maxClients:%d\n",
				st.getAverage(true), st.getDP(true), st.getMax(true), minClients, maxClients);
		numMaxRealClients[round - 1] = (int) maxClients;
		avgThroughput[round - 1] = st.getAverage(true);
		throughputDev[round - 1] = st.getDP(true);
		maxThroughput[round - 1] = st.getMax(true);
		measurementDelivered.countDown();
	}

	@Override
	public void deliverClientProcessingResult(IProcessingResult processingResult) {
		Measurement measurement = (Measurement) processingResult;
		System.out.println("Received client measurement result");
		saveClientMeasurements(round, measurement);
		Storage st = new Storage(measurement.getMeasurements()[0]);
		System.out.printf("Client Measurement[ms] - avg:%f dev:%f max:%d\n", st.getAverage(true) / 1000000,
				st.getDP(true) / 1000000, st.getMax(true) / 1000000);
		avgLatency[round - 1] = st.getAverage(true);
		latencyDev[round - 1] = st.getDP(true);
		maxLatency[round - 1] = st.getMax(true);
		measurementDelivered.countDown();
	}

	public void saveServerMeasurements(int round, Measurement measurement) {
		String fileName = "server_" + round + ".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			long[] clients = measurement.getMeasurements()[0];
			long[] numRequests = measurement.getMeasurements()[1];
			long[] delta = measurement.getMeasurements()[2];
			int size = clients.length;
			resultFile.write("clients(#),requests(#),delta(ns)\n");
			for (int i = 0; i < size; i++) {
				resultFile.write(String.format("%d,%d,%d\n", clients[i], numRequests[i], delta[i]));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void saveClientMeasurements(int round, Measurement measurement) {
		String fileName = "client_" + round + ".csv";
		try (BufferedWriter resultFile = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName)))) {
			long[] latency = measurement.getMeasurements()[0];
			resultFile.write("latency(ns)\n");
			for (long l : latency) {
				resultFile.write(String.format("%d\n", l));
			}
			resultFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sleepSeconds(long duration) throws InterruptedException {
		lock.lock();
		Executors.newSingleThreadScheduledExecutor().schedule(() -> {
			lock.lock();
			sleepCondition.signal();
			lock.unlock();
		}, duration, TimeUnit.SECONDS);
		sleepCondition.await();
		Executors.newSingleThreadExecutor().shutdown();
		lock.unlock();
	}

}