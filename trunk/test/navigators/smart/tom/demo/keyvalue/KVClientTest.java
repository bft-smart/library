package navigators.smart.tom.demo.keyvalue;

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KVClientTest {

	private static Process replica1;
	private static Process replica2;
	private static Process replica3;
	private static Process replica4;
	private static String[] command = new String[5];

	@BeforeClass
	public static void startServers() {
		try {
			System.out.println("Starting the servers");
			command[0] = "java";
			command[1] = "-cp";
			command[2] = "bin/SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar";
			command[3] = "navigators.smart.tom.demo.keyvalue.BFTMapImpl";
			command[4] = "0";
			
			replica1 = new ProcessBuilder(command).redirectErrorStream(true).start();
			command[4] = "1";
			replica2 = new ProcessBuilder(command).redirectErrorStream(true).start();
			command[4] = "2";
			replica3 = new ProcessBuilder(command).redirectErrorStream(true).start();
			command[4] = "3";
			replica4 = new ProcessBuilder(command).redirectErrorStream(true).start();

			LogWriter lwriter1 = new LogWriter();
			lwriter1.setIn(replica1.getInputStream());
			lwriter1.setIndex(1);
			lwriter1.start();

			LogWriter lwriter2 = new LogWriter();
			lwriter2.setIn(replica2.getInputStream());
			lwriter2.setIndex(2);
			lwriter2.start();

			LogWriter lwriter3 = new LogWriter();
			lwriter3.setIn(replica3.getInputStream());
			lwriter3.setIndex(3);
			lwriter3.start();

			LogWriter lwriter4 = new LogWriter();
			lwriter4.setIn(replica4.getInputStream());
			lwriter4.setIndex(4);
			lwriter4.start();

			System.out.println("Servers started");

		} catch(IOException ioe) {
			System.out.println("Exception during KVClient test: ");
			System.out.println(ioe.getMessage());
		}
	}

	@AfterClass
	public static void stopServers() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, IOException  {
		System.out.println("Stopping servers");
		replica1.destroy();
		replica2.destroy();
		replica3.destroy();
		replica4.destroy();
		System.out.println("Servers stopped");
	}

	/**
	 * Test regular case where there is the creation of a table, insert of
	 * data and search for size of the table.
	 * No servers are killed nor behaves different than expected.
	 */
	@Test
	public void testRegularCase() {
		try{
			Thread.sleep(1000);
			BFTMap bftMap = new BFTMap(1001);
			bftMap.put("TestTable1", new HashMap<String,byte[]>());
			bftMap.putEntry("TestTable1", "key1", "value1".getBytes());
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable1"));
			
			for(int i = 0; i < 100; i++) {
				String key = "key" + (2+i);
				String value = "value" + (2+i);
				byte[] result =	bftMap.putEntry("TestTable1", key, value.getBytes());
			}
			assertEquals("Main table size should be 101", 101, bftMap.size1("TestTable1"));

			bftMap.putEntry("TestTable1", "key102", "value102".getBytes());
			assertEquals("Main table size should be 102", 102, bftMap.size1("TestTable1"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

	/**
	 * This test insert and retrieve data.
	 * During this process a replica that is not the leader is killed.
	 * After that the replica is started back.
	 * During the whole process messages keep being sent, to test if
	 * the application works as expected.
	 */
//	@Test
//	public void testStopAndStartNonLeader() {
//		try{
//			Thread.sleep(1000);
//			BFTMap bftMap = new BFTMap(1001);
//			bftMap.put("TestTable2", new HashMap<String,byte[]>());
//			bftMap.putEntry("TestTable2", "key1", "value1".getBytes());
//			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable2"));
//			
//			for(int i = 0; i < 200; i++) {
//				String key = "key" + (2+i);
//				String value = "value" + (2+i);
//				bftMap.putEntry("TestTable2", key, value.getBytes());
//			}
//			assertEquals("Main table size should be 201", 201, bftMap.size1("TestTable2"));
//
//			replica2.destroy(); // Killing a non-leader replica, replica2
//			for(int i = 0; i < 100; i++) {
//				String key = "key" + (202+i);
//				String value = "value" + (202+i);
//				bftMap.putEntry("TestTable2", key, value.getBytes());
//			}
//			assertEquals("Main table size should be 301", 301, bftMap.size1("TestTable2"));
//
//			command[4] = "1";
//			replica2 = new ProcessBuilder(command).start(); // Starting replica2 back
//			for(int i = 0; i < 100; i++) {
//				String key = "key" + (302+i);
//				String value = "value" + (302+i);
//				bftMap.putEntry("TestTable2", key, value.getBytes());
//			}
//			assertEquals("Main table size should be 401", 401, bftMap.size1("TestTable2"));
//			
//			Thread.sleep(1000);
//			replica3.destroy(); // Killing another non-leader replica, replica3
//			for(int i = 0; i < 100; i++) {
//				String key = "key" + (402+i);
//				String value = "value" + (402+i);
//				bftMap.putEntry("TestTable2", key, value.getBytes());
//			}
//			assertEquals("Main table size should be 501", 501, bftMap.size1("TestTable2"));
//
//			command[4] = "2";
//			replica3 = new ProcessBuilder(command).start(); // Starting replica2 back
//			for(int i = 0; i < 100; i++) {
//				String key = "key" + (502+i);
//				String value = "value" + (502+i);
//				bftMap.putEntry("TestTable2", key, value.getBytes());
//			}
//			assertEquals("Main table size should be 601", 601, bftMap.size1("TestTable2"));
//			
//		} catch(InterruptedException ie) {
//			System.out.println("Exception during Thread sleep: " + ie.getMessage());
//		} catch(IOException ioe) {
//			System.out.println("Exception when starting replica 2: " + ioe.getMessage());
//		}
//	}
}
