package bftsmart;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import bftsmart.reconfiguration.StatusReply;
import bftsmart.reconfiguration.TTP;

public class TestFixture {

	private static Process replica0;
	private static Process replica1;
	private static Process replica2;
	private static Process replica3;
	
	private static ConsoleLogger log0;
	private static ConsoleLogger log1;
	private static ConsoleLogger log2;
	private static ConsoleLogger log3;

	private static String[] command = new String[5];
	
	private static TTP ttp;

	@BeforeClass
	public static void startServers() {
		try {
			System.out.println("Starting the servers");
			command[0] = "java";
			command[1] = "-cp";
			command[2] = "bin/BFT-SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar";
			command[3] = "bftsmart.demo.bftmap.BFTMapServer";
			command[4] = "0";
			
			startServer(0);

			Thread.sleep(2000);
			startServer(1);

			Thread.sleep(2000);
			startServer(2);
			
			Thread.sleep(2000);
			startServer(3);

		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

	@AfterClass
	public static void stopServers() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, IOException  {
		System.out.println("Stopping servers");
		stopServer(0);
		stopServer(1);
		stopServer(2);
		stopServer(3);
		System.out.println("Servers stopped");
	}
	
	public static void stopServer(int id) {
		switch(id) {
		case 0:
			replica0.destroy();
			break;
		case 1:
			replica1.destroy();
			break;
		case 2:
			replica2.destroy();
			break;
		case 3:
			replica3.destroy();
			break;
		default:
			System.out.println("### Couldn't stop server. Server not found ###");
			break;
		}
	}

	public static void startServer(int id) {
		command[4] = String.valueOf(id);
		try {
			switch(id) {
			case 0:
				replica0 = new ProcessBuilder(command).redirectErrorStream(true).start();
				log0 = new ConsoleLogger();
				log0.setIn(replica0.getInputStream());
				log0.setOut(System.out);
				log0.setIndex(String.valueOf(id));
				log0.start();
				break;
			case 1:
				replica1 = new ProcessBuilder(command).redirectErrorStream(true).start();
				log1 = new ConsoleLogger();
				log1.setIn(replica1.getInputStream());
				log1.setOut(System.out);
				log1.setIndex(String.valueOf(id));
				log1.start();
				break;
			case 2:
				replica2 = new ProcessBuilder(command).redirectErrorStream(true).start();
				log2 = new ConsoleLogger();
				log2.setIn(replica2.getInputStream());
				log2.setOut(System.out);
				log2.setIndex(String.valueOf(id));
				log2.start();
				break;
			case 3:
				replica3 = new ProcessBuilder(command).redirectErrorStream(true).start();
				log3 = new ConsoleLogger();
				log3.setIn(replica3.getInputStream());
				log3.setOut(System.out);
				log3.setIndex(String.valueOf(id));
				log3.start();
				break;
			default:
				System.out.println("Id not supported");
				break;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static StatusReply askStatus(int replicaId) {
		if(ttp == null)
			ttp = new TTP();
		StatusReply reply = ttp.askStatus(replicaId);
		System.out.println("---- Status atual: " + reply);
		return reply;
	}
}
