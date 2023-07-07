package bftsmart.tests.recovery;

import controller.IBenchmarkStrategy;
import controller.IWorkerStatusListener;
import controller.WorkerHandler;
import worker.IProcessingResult;
import worker.ProcessInformation;

import java.io.*;
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
 * @author robin
 */
public class RecoveryTestStrategy implements IBenchmarkStrategy, IWorkerStatusListener {
	private final String clientCommand;
	private final String serverCommand;
	private final int dataSize;
	private final Set<Integer> serverWorkersIds;
	private final Set<Integer> clientWorkersIds;
	private final Lock lock;
	private final Condition sleepCondition;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;
	private final AtomicBoolean error;

	public RecoveryTestStrategy() {
		this.dataSize = 1024;
		int nLoadRequests = 1_000_000;
		this.clientCommand =  "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* " +
				"bftsmart.tests.recovery.RecoveryTestClient " + "1000 " + nLoadRequests + " " + dataSize + " " + true;
		this.serverCommand = "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* " +
				"bftsmart.tests.recovery.RecoveryTestServer ";
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
		this.serverWorkersIds = new HashSet<>();
		this.clientWorkersIds = new HashSet<>();
		this.error = new AtomicBoolean(false);
	}

	@Override
	public void executeBenchmark(WorkerHandler[] workerHandlers, Properties benchmarkParameters) {
		System.out.println("Running recovery test");
		int f = Integer.parseInt(benchmarkParameters.getProperty("experiment.f"));
		String workingDirectory = benchmarkParameters.getProperty("experiment.working_directory");
		boolean isbft = Boolean.parseBoolean(benchmarkParameters.getProperty("experiment.bft"));

		String hosts = "0 127.0.0.1 11000 11001\n" + 
				"1 127.0.0.1 11010 11011\n" + 
				"2 127.0.0.1 11020 11021\n" + 
				"3 127.0.0.1 11030 11031\n" + 
				"\n7001 127.0.0.1 11100";

		int nServers = (isbft ? 3*f+1 : 2*f+1);

		//Separate workers
		WorkerHandler[] serverWorkers = new WorkerHandler[nServers];
		WorkerHandler clientWorker = workerHandlers[workerHandlers.length - 1];
		System.arraycopy(workerHandlers, 0, serverWorkers, 0, nServers);
		Arrays.stream(serverWorkers).forEach(w -> serverWorkersIds.add(w.getWorkerId()));
		clientWorkersIds.add(clientWorker.getWorkerId());

		//Setup workers
		String setupInformation = String.format("%b\t%d\t%s", isbft, f, hosts);
		Arrays.stream(workerHandlers).forEach(w -> w.setupWorker(setupInformation));

		try {
			lock.lock();
			//Start servers
			startServers(workingDirectory, serverWorkers);
			if (error.get())
				return;

			//Start client that continuously send requests
			startClients(workingDirectory, clientWorker);
			if (error.get())
				return;

			System.out.println("Clients are sending requests");

			//Restarting servers to test recovery
			for (int server = nServers - 1; server >= 0; server--) {
				WorkerHandler serverWorker = serverWorkers[server];
				System.out.println("Rebooting server " + server + " in 10 seconds [reboot will take around 20 seconds]");
				sleepSeconds(10);

				//Stop server
				serverWorker.stopWorker();

				sleepSeconds(20);

				//Start server
				serversReadyCounter = new CountDownLatch(1);
				String command = serverCommand + server + " " + dataSize;
				String currentWorkerDirectory = workingDirectory + "worker" + serverWorker.getWorkerId()
						+ File.separator;
				ProcessInformation[] commands = {
						new ProcessInformation(command, currentWorkerDirectory)
				};

				serverWorker.startWorker(0, commands, this);
				serversReadyCounter.await();
				if (error.get())
					return;
			}
			System.out.println("Waiting 10 seconds");
			sleepSeconds(10);
			System.out.println("Recovery test was a success");
		} catch (InterruptedException ignore) {
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			lock.unlock();
		}
	}

	private void startClients(String workingDirectory, WorkerHandler... clientWorkers) throws InterruptedException, IOException {
		System.out.println("Starting client...");
		clientsReadyCounter = new CountDownLatch(1);
		String currentWorkerDirectory = workingDirectory + "worker" + clientWorkers[0].getWorkerId()
				+ File.separator;
		ProcessInformation[] commands = new ProcessInformation[] {
				new ProcessInformation(clientCommand, currentWorkerDirectory)
		};
		clientWorkers[0].startWorker(50, commands, this);
		clientsReadyCounter.await();
	}

	private void startServers(String workingDirectory, WorkerHandler... serverWorkers) throws InterruptedException, IOException {
		System.out.println("Starting servers...");
		serversReadyCounter = new CountDownLatch(serverWorkers.length);
		for (int i = 0; i < serverWorkers.length; i++) {
			String command = serverCommand + i + " " + dataSize;
			WorkerHandler serverWorker = serverWorkers[i];
			String currentWorkerDirectory = workingDirectory + "worker" + serverWorker.getWorkerId()
					+ File.separator;
			ProcessInformation[] commands = {
					new ProcessInformation(command, currentWorkerDirectory)
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
			System.err.printf("Error in server worker %d\n", workerId);
			if (serversReadyCounter != null) {
				serversReadyCounter.countDown();
			}
		} else if (clientWorkersIds.contains(workerId)) {
			System.err.printf("Error in client worker %d\n", workerId);
			if (clientsReadyCounter != null)
				clientsReadyCounter.countDown();
		} else {
			System.out.printf("Error in unused worker %d\n", workerId);
		}
		error.set(true);
	}

	@Override
	public void onResult(int workerId, IProcessingResult processingResult) {

	}
}
