package bftsmart.tests.normal;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class NormalRequestsTestStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final Lock lock;
	private final Condition sleepCondition;
	private final String commandPrefix;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private final AtomicBoolean error;
	private final Semaphore ended;

	public NormalRequestsTestStrategy() {
		this.commandPrefix = "java -Djava.security.properties=./config/java.security " +
				"-Dlogback.configurationFile=./config/logback.xml -cp lib/* ";
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.error = new AtomicBoolean(false);
		this.ended = new Semaphore(0);
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workerHandlers, Properties properties) {
		int nOperations = 5_000;
		int stateSize = 100;
		String clientClass = properties.getProperty("experiment.clientClass");
		String requestType = properties.getProperty("experiment.requestType");
		boolean isBFT = Boolean.parseBoolean(properties.getProperty("experiment.bft"));
		int f = Integer.parseInt(properties.getProperty("experiment.f"));
		boolean isUnorderedRequestEnabled = Boolean.parseBoolean(properties.getProperty("experiment.isUnorderedRequestEnabled"));

		String clientCommand = commandPrefix + clientClass + " 100 " + requestType + " " + nOperations + " " + stateSize;
		String serverCommand = commandPrefix + "bftsmart.tests.common.SimpleServiceServer " + stateSize + " ";

		int nServers = isBFT ? 3 * f + 1 : 2 * f + 1;
		String hosts = generateLocalHosts(nServers);
		if (workerHandlers.length < nServers + 1) {
			throw new IllegalArgumentException("Not enough workers to run the experiment");
		}

		//Separate workers into servers and client
		WorkerHandler[] serverWorkers = new WorkerHandler[nServers];
		WorkerHandler clientWorker = workerHandlers[workerHandlers.length - 1];
		System.arraycopy(workerHandlers, 0, serverWorkers, 0, nServers);
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		clientWorkersIds.add(clientWorker.getWorkerId());

		//Setup workers
		String setupInformation = String.format("%b\t%d\t%s\t%s", isBFT, f, hosts, isUnorderedRequestEnabled);
		Arrays.stream(workerHandlers).forEach(w -> w.setupWorker(setupInformation));

		try {
			lock.lock();

			//Start servers
			startServers(serverCommand, serverWorkers);
			if (error.get())
				return;

			//Start client that continuously send requests
			startClient(clientCommand, clientWorker);
			if (error.get())
				return;

			ended.acquire();

			sleepSeconds(5);

			//Stop processes
			clientWorker.stopWorker();
			Arrays.stream(serverWorkers).forEach(WorkerHandler::stopWorker);

			sleepSeconds(5);

			if (error.get()) {
				logger.error("failed");
			} else {
				logger.info("success");
			}
		} catch (InterruptedException ignore) {
		} finally {
			lock.unlock();
		}
	}

	private void startClient(String clientCommand, WorkerHandler clientWorker) throws InterruptedException {
		logger.debug("Starting client...");
		clientsReadyCounter = new CountDownLatch(1);
		ProcessInformation[] commands = new ProcessInformation[] {
				new ProcessInformation(clientCommand, ".")
		};

		clientWorker.startWorker(50, commands, this);
		clientsReadyCounter.await();
	}

	private void startServers(String serverCommand, WorkerHandler... serverWorkers) throws InterruptedException {
		logger.debug("Starting servers...");
		serversReadyCounter = new CountDownLatch(serverWorkers.length);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + i;
			WorkerHandler serverWorker = serverWorkers[i];
			ProcessInformation[] commands = {
					new ProcessInformation(command, ".")
			};
			serverWorker.startWorker(0, commands, this);
			sleepSeconds(1);
		}
		serversReadyCounter.await();
	}

	private String generateLocalHosts(int nServers) {
		StringBuilder hosts = new StringBuilder();
		for (int i = 0; i < nServers; i++) {
			hosts.append(i).append(" ").append("127.0.0.1").append(" ").append(11000 + 10 * i).append(" ")
					.append(11001 + 10 * i + 1).append("\n");
		}
		hosts.append("\n7001 127.0.0.1 11100");
		return hosts.toString();
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
		ended.release();
	}

	@Override
	public void onError(int workerId, String errorMessage) {
		if (serverWorkersIds.contains(workerId)) {
			//logger.error("Error in server worker {}: {}", workerId, errorMessage);
			if (serversReadyCounter != null) {
				serversReadyCounter.countDown();
			}
		} else if (clientWorkersIds.contains(workerId)) {
			//logger.error("Error in client worker {}: {}", workerId, errorMessage);
			if (clientsReadyCounter != null)
				clientsReadyCounter.countDown();
		} else {
			logger.error("Error in unused worker {}: {}", workerId, errorMessage);
		}
		error.set(true);
		ended.release();
	}

	@Override
	public void onResult(int i, IProcessingResult iProcessingResult) {

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
