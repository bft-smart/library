package bftsmart.tests.recovery;

import controller.IBenchmarkStrategy;
import master.IProcessingResult;
import master.client.ClientsMaster;
import master.message.Message;
import master.server.ServersMaster;
import pod.ProcessInfo;
import pod.WorkerCommands;
import util.Configuration;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author robin
 */
public class RecoveryTestStrategy implements IBenchmarkStrategy {
	private final String workingDirectory;
	private final String clientCommand;
	private final String serverCommand;
	private final int numServers;
	private final int dataSize;
	private final Lock lock;
	private final Condition sleepCondition;
	private CountDownLatch serversReadyCounter;
	private CountDownLatch clientsReadyCounter;

	public RecoveryTestStrategy() {
		int numLoadRequests = 1000_000;
		this.workingDirectory = Configuration.getInstance().getWorkingDirectory();
		dataSize = Configuration.getInstance().getDataSize();
		numServers = Configuration.getInstance().getNumServerPods();
		this.clientCommand =  "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* bftsmart.tests.recovery.RecoveryTestClient " +
				"100 " + numLoadRequests + " " + dataSize + " " + true;
		this.serverCommand = "java -Djava.security.properties=./config/java" +
				".security -Dlogback.configurationFile=./config/logback.xml -cp lib/* bftsmart.tests.recovery.RecoveryTestServer ";
		this.lock = new ReentrantLock(true);
		this.sleepCondition = lock.newCondition();
	}

	@Override
	public void executeBenchmark(ServersMaster serversMaster, long[] serverPodsIds, ClientsMaster clientsMaster, long[] clientPodsIds) {
			try {
				lock.lock();

				System.out.println("Starting servers");
				serversReadyCounter = new CountDownLatch(numServers);
				WorkerCommands[] serverCommands = new WorkerCommands[numServers];
				for (int i = 0; i < numServers; i++) {
					ProcessInfo processInfo = new ProcessInfo(serverCommand + i + " " + dataSize, workingDirectory);
					serverCommands[i] = new WorkerCommands(serverPodsIds[i], new ProcessInfo[]{processInfo});
				}
				serversMaster.startWorkers(0, serverCommands);

				serversReadyCounter.await();
				System.out.println("Servers are ready");

				System.out.println("Starting load client");
				clientsReadyCounter = new CountDownLatch(1);
				WorkerCommands[] clientCommands = new WorkerCommands[] {
						new WorkerCommands(
								clientPodsIds[0],
								new ProcessInfo[] {
										new ProcessInfo(clientCommand, workingDirectory)
								}
						)
				};
				clientsMaster.startWorkers(0, clientCommands);

				clientsReadyCounter.await();
				System.out.println("Clients are sending requests");

				for (int server = numServers - 1; server >= 0; server--) {
					sleepSeconds(20);
					System.out.println("Restarting server " + server);
					long podId = serverPodsIds[server];
					serversMaster.stopWorker(podId);
					sleepSeconds(10);
					serversReadyCounter = new CountDownLatch(1);
					WorkerCommands commands = new WorkerCommands(podId, new ProcessInfo[]{
							new ProcessInfo(serverCommand + server + " " + dataSize, workingDirectory)
					});
					serversMaster.startWorker(0, commands);
					serversReadyCounter.await();
				}
				System.out.println("All servers were restarted");
				sleepSeconds(30);
			} catch (InterruptedException ignored) {
			} finally {
				lock.unlock();
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

	@Override
	public void processServerReadyEvent(Message message) {
		serversReadyCounter.countDown();
	}

	@Override
	public void processClientReadyEvent(Message message) {
		clientsReadyCounter.countDown();
	}

	@Override
	public void deliverServerProcessingResult(IProcessingResult processingResult) {
		System.out.println("Received server measurement result");
	}

	@Override
	public void deliverClientProcessingResult(IProcessingResult processingResult) {
		System.out.println("Received client measurement result");
	}
}
