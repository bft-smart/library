package bftsmart.tests.recovery;

import java.io.IOException;

/**
 * @author robin
 */
public class RecoveryTest {
	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 1) {
			throw new IllegalArgumentException("USAGE: bftsmart.tests.recovery.RecoveryTest <working directory>");
		}
		System.out.println("Running recovery test");
		String workingDirectory = args[0];
		String controllerCommand = "java -cp lib\\* controller.BenchmarkControllerStartup benchmark.config";
		String clientPodCommand = "java -cp lib\\* pod.PodStartup 127.0.0.1 12000 bftsmart.tests.recovery.RecoveryTestClientEventProcessor";
		String serverPodCommand = "java -cp lib\\* pod.PodStartup 127.0.0.1 12001 bftsmart.tests.recovery.RecoveryTestServerEventProcessor";

		ProcessExecutor controller = new ProcessExecutor(workingDirectory, controllerCommand);

		int nServers = 4;
		int nClients = 1;
		ProcessExecutor[] servers = new ProcessExecutor[nServers];
		for (int i = 0; i < nServers; i++) {
			String currentServerDirectory = workingDirectory + "rep" + i + "\\";
			servers[i] = new ProcessExecutor(currentServerDirectory, serverPodCommand);
		}

		ProcessExecutor[] clients = new ProcessExecutor[nClients];
		for (int i = 0; i < nClients; i++) {
			String currentClientDirectory = workingDirectory + "cli" + i + "\\";
			clients[i] = new ProcessExecutor(currentClientDirectory, clientPodCommand);
		}

		controller.start();
		Thread.sleep(3000);

		for (ProcessExecutor server : servers) {
			server.start();
		}

		for (ProcessExecutor client : clients) {
			client.start();
		}

		controller.join();
		for (ProcessExecutor server : servers) {
			server.join();
		}

		for (ProcessExecutor client : clients) {
			client.join();
		}
		System.out.println("Test terminated");
	}
}
