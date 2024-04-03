package bftsmart.tests.recovery;

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
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author robin
 */
public class RecoveryTestStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final Logger logger = LoggerFactory.getLogger("benchmarking");
	private final String clientCommand;
	private final String serverCommand;
	private final int dataSize;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final Lock lock;
	private final Condition sleepCondition;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;

	public RecoveryTestStrategy() {
		this.dataSize = 1024;
		int nLoadRequests = 1_000_000;
		this.clientCommand =  "java -Djava.security.properties=config/java" +
				".security -Dlogback.configurationFile=config/logback.xml -cp lib/* " +
				"bftsmart.tests.recovery.RecoveryTestClient " + "1000 " + nLoadRequests + " " + dataSize + " " + true;
		this.serverCommand = "java -Djava.security.properties=config/java" +
				".security -Dlogback.configurationFile=config/logback.xml -cp lib/* " +
				"bftsmart.tests.recovery.RecoveryTestServer ";
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workerHandlers, Properties benchmarkParameters) {
		logger.info("Running recovery strategy");
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		boolean isBFT = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.bft"));

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
		String setupInformation = String.format("%b\t%d\t%s", isBFT, f, hosts);
		Arrays.stream(workerHandlers).forEach(w -> w.setupWorker(setupInformation));

		try {
			lock.lock();
			//Start servers
			startServers(serverWorkers);

			//Start client that continuously send requests
			startClients(clientWorker);

			logger.info("Clients are sending requests");

			//Restarting servers to test recovery
			for (int server = nServers - 1; server >= 0; server--) {
				WorkerHandler serverWorker = serverWorkers[server];
				logger.info("Rebooting server {} in 10 seconds [reboot will take around 20 seconds]", server);
				sleepSeconds(10);

				//Stop server
				serverWorker.stopWorker();

				sleepSeconds(20);

				//Start server
				serversReadyCounter = new CountDownLatch(1);
				String command = serverCommand + server + " " + dataSize;
				ProcessInformation[] commands = {
						new ProcessInformation(command, ".")
				};

				serverWorker.startWorker(0, commands, this);
				serversReadyCounter.await();
			}
			logger.info("Waiting 10 seconds");
			sleepSeconds(10);
			logger.info("Recovery test was a success");
		} catch (InterruptedException ignore) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	private void startClients(WorkerHandler... clientWorkers) throws InterruptedException, IOException {
		logger.info("Starting clients...");
		clientsReadyCounter = new CountDownLatch(1);
		ProcessInformation[] commands = new ProcessInformation[] {
				new ProcessInformation(clientCommand, ".")
		};
		clientWorkers[0].startWorker(50, commands, this);
		clientsReadyCounter.await();
	}

	private void startServers(WorkerHandler... serverWorkers) throws InterruptedException, IOException {
		logger.info("Starting servers...");
		serversReadyCounter = new CountDownLatch(serverWorkers.length);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + i + " " + dataSize;
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
			logger.debug("Error in server worker {}: {}", workerId, errorMessage);
		} else if (clientWorkersIds.contains(workerId)) {
			logger.debug("Error in client worker {}: {}", workerId, errorMessage);
		} else {
			logger.debug("Error in unused worker {}: {}", workerId, errorMessage);
		}
	}

	@Override
	public void onResult(int workerId, IProcessingResult processingResult) {

	}
}
