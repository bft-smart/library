package bftsmart.tests.recovery;

import java.io.File;
import java.io.IOException;

/**
 * @author robin
 */
public class RecoveryTest {
	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length != 3) {
			throw new IllegalArgumentException("USAGE: bftsmart.tests.recovery.RecoveryTest <working directory> <nServers> <nClients>");
		}
		String workingDirectory = args[0];
		int nServers = Integer.parseInt(args[1]);
		int nClients = Integer.parseInt(args[2]);
		System.out.println("Running recovery test");
		String path = "lib" + File.separator + "*";
		System.out.println("lib path: " + path);
		String controllerCommand = "java -cp " + path +" bftsmart.tests.BenchmarkControllerStarter" +
				" master.listening.ip=127.0.0.1" +
				" master.listening.port.client=12000" +
				" master.listening.port.server=12001" +
				" master.clients=" + nClients +
				" master.servers=" + nServers +
				" pod.network.interface=0" +
				" controller.benchmark.strategy=bftsmart.tests.recovery.RecoveryTestStrategy" +
				" master.rounds=1" +
				" global.working.directory=." +
				" global.clients.maxPerWorker=1" +
				" global.isWrite=true" +
				" global.data.size=1024";
		String clientPodCommand = "java -cp " +  path +" pod.PodStartup 127.0.0.1 12000 bftsmart.tests.recovery.RecoveryTestClientEventProcessor";
		String serverPodCommand = "java -cp " +  path +" pod.PodStartup 127.0.0.1 12001 bftsmart.tests.recovery.RecoveryTestServerEventProcessor";

		ProcessExecutor controller = new ProcessExecutor(workingDirectory, controllerCommand);

		ProcessExecutor[] servers = new ProcessExecutor[nServers];
		for (int i = 0; i < nServers; i++) {
			String currentServerDirectory = workingDirectory + "rep" + i + File.separator;
			servers[i] = new ProcessExecutor(currentServerDirectory, serverPodCommand);
		}

		ProcessExecutor[] clients = new ProcessExecutor[nClients];
		for (int i = 0; i < nClients; i++) {
			String currentClientDirectory = workingDirectory + "cli" + i + File.separator;
			clients[i] = new ProcessExecutor(currentClientDirectory, clientPodCommand);
		}

		System.out.println("Starting controller");
		controller.start();
		Thread.sleep(3000);

		System.out.println("Starting servers");
		for (ProcessExecutor server : servers) {
			server.start();
		}

		System.out.println("Starting clients");
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
