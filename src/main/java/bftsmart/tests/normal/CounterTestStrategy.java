package bftsmart.tests.normal;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author nuria
 */
public class CounterTestStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final String clientCommand;
	private final String serverCommand;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final Lock lock;
	private final Condition sleepCondition;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private final AtomicBoolean error;

	public CounterTestStrategy() {
		int increment = 5;
		int nLoadRequests = 1_000;
		this.clientCommand =  "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* " +
				"bftsmart.tests.normal.CounterTestClient " + "1000 " + nLoadRequests + " " + increment;
		this.serverCommand = "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* " +
				"bftsmart.demo.counter.CounterServer ";
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.error = new AtomicBoolean(false);
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workerHandlers, Properties benchmarkParameters) {
		logger.info("Running counter strategy");
		boolean isBFT = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.bft"));
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String hosts = "0 127.0.0.1 11000 11001\n" + 
					   "1 127.0.0.1 11010 11011\n" + 
					   "2 127.0.0.1 11020 11021\n" + 
					   "3 127.0.0.1 11030 11031\n" + 
					   "\n7001 127.0.0.1 11100";
		
		int nServers = (isBFT ? 3*f+1 : 2*f+1);

		//Separate workers
		WorkerHandler[] serverWorkers = new WorkerHandler[nServers];
		WorkerHandler clientWorker = workerHandlers[workerHandlers.length - 1];
		System.arraycopy(workerHandlers, 0, serverWorkers, 0, nServers);
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		clientWorkersIds.add(clientWorker.getWorkerId());

		//Setup workers
		String setupInformation = String.format("%b\t%d\t"+hosts, isBFT, f);
		Arrays.stream(workerHandlers).forEach(w -> w.setupWorker(setupInformation));

		try {
			lock.lock();
			//Start servers
			startServers(serverWorkers);
			if (error.get())
				return;

			//Start client that continuously send requests
			startClients(clientWorker);
			if (error.get())
				return;

			logger.info("Client sending requests...");

			logger.info("Waiting 10 seconds");
			sleepSeconds(10);

			//Stop processes
			Arrays.stream(serverWorkers).forEach(WorkerHandler::stopWorker);
			clientWorker.stopWorker();

			sleepSeconds(5);

			if (error.get()) {
				logger.error("Counter test failed");
			} else {
				logger.info("Counter test was a success");
			}
		} catch (InterruptedException ignore) {
		} finally {
			lock.unlock();
		}
	}

	private void startClients(WorkerHandler... clientWorkers) throws InterruptedException {
		logger.debug("Starting client...");
		clientsReadyCounter = new CountDownLatch(1);
		ProcessInformation[] commands = new ProcessInformation[] {
			new ProcessInformation(clientCommand, ".")
		};

		clientWorkers[0].startWorker(50, commands, this);
		clientsReadyCounter.await();
	}

	private void startServers(WorkerHandler... serverWorkers) throws InterruptedException {
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
			logger.error("Error in server worker {}", workerId);
			if (serversReadyCounter != null) {
				serversReadyCounter.countDown();
			}
		} else if (clientWorkersIds.contains(workerId)) {
			logger.error("Error in client worker {}", workerId);
			if (clientsReadyCounter != null)
				clientsReadyCounter.countDown();
		} else {
			logger.error("Error in unused worker {}", workerId);
		}
		error.set(true);
	}

	@Override
	public void onResult(int workerId, IProcessingResult processingResult) {

	}
}
