/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.demo.bftmapjunit;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import bftsmart.demo.bftmap.BFTMap;

/**
 * 
 * @author Marcel Santos
 *
 */
public class KVClientTest {

	private static Process replica0;
	private static Process replica1;
	private static Process replica2;
	private static Process replica3;
	private static String[] command = new String[5];

	@BeforeClass
	public static void startServers() {
		try {
			System.out.println("Starting the servers");
			command[0] = "java";
			command[1] = "-cp";
			command[2] = "bin/BFT-SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar";
			command[3] = "bftsmart.demo.bftmap.BFTMapServer";
			command[4] = "0";
			
			replica0 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log0 = new ConsoleLogger();;
			log0.setIn(replica0.getInputStream());
			log0.setOut(System.out);
			log0.setIndex("0");
			log0.start();
			Thread.sleep(2000);
			command[4] = "1";
			replica1 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log1 = new ConsoleLogger();;
			log1.setIn(replica1.getInputStream());
			log1.setOut(System.out);
			log1.setIndex("1");
			log1.start();
			Thread.sleep(2000);
			command[4] = "2";
			replica2 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log2 = new ConsoleLogger();;
			log2.setIn(replica2.getInputStream());
			log2.setOut(System.out);
			log2.setIndex("2");
			log2.start();
			Thread.sleep(2000);
			command[4] = "3";
			replica3 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log3 = new ConsoleLogger();;
			log3.setIn(replica3.getInputStream());
			log3.setOut(System.out);
			log3.setIndex("3");
			log3.start();
		    System.out.println("Servers started");

		} catch(IOException ioe) {
			System.out.println("Exception during BFTMapInteractiveClient test: ");
			System.out.println(ioe.getMessage());
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

	@AfterClass
	public static void stopServers() throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, IOException  {
		System.out.println("Stopping servers");
		replica0.destroy();
		replica1.destroy();
		replica2.destroy();
		replica3.destroy();
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
				bftMap.putEntry("TestTable1", key, value.getBytes());
			}
			assertEquals("Main table size should be 101", 101, bftMap.size1("TestTable1"));

			bftMap.putEntry("TestTable1", "key102", "value102".getBytes());
			assertEquals("Main table size should be 102", 102, bftMap.size1("TestTable1"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

	/**
	 * - Crate table;
	 * - Insert data;
	 * - Kills a replica that is not a leader;
	 * - Insert data;
	 * - Verify if the size of the table is correct.
	 */
	@Test
	public void testStopNonLeader() {
		try{
			Thread.sleep(1000);
			BFTMap bftMap = new BFTMap(1001);
			bftMap.put("TestTable2", new HashMap<String,byte[]>());
			bftMap.putEntry("TestTable2", "key1", "value1".getBytes());
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable2"));
			
			for(int i = 0; i < 200; i++) {
				String key = "key" + (2+i);
				String value = "value" + (2+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}
			assertEquals("Main table size should be 201", 201, bftMap.size1("TestTable2"));

			replica2.destroy(); // Killing a non-leader replica, replica2
			for(int i = 0; i < 200; i++) {
				String key = "key" + (202+i);
				String value = "value" + (202+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}
			assertEquals("Main table size should be 401", 401, bftMap.size1("TestTable2"));

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
	@Test
	public void testStopAndStartNonLeader() {
		try{
			Thread.sleep(5000);
			BFTMap bftMap = new BFTMap(1001);
			Thread.sleep(1000);
			bftMap.put("TestTable3", new HashMap<String,byte[]>());
			
			for(int i = 0; i < 65; i++) {
				String key = "key" + (1+i);
				String value = "value" + (1+i);
				System.out.println(bftMap.putEntry("TestTable3", key, value.getBytes()));
			}
			assertEquals("Main table size should be 65", 65, bftMap.size1("TestTable3"));

			replica1.destroy(); // Killing a non-leader replica, replica2
			for(int i = 0; i < 35; i++) {
				String key = "key" + (66+i);
				String value = "value" + (66+i);
				System.out.println(bftMap.putEntry("TestTable3", key, value.getBytes()));
			}
			assertEquals("Main table size should be 100", 100, bftMap.size1("TestTable3"));

			command[4] = "1";
			replica1 = new ProcessBuilder(command).redirectErrorStream(true).start(); // Starting replica2 back
			ConsoleLogger log1 = new ConsoleLogger();;
			log1.setIn(replica1.getInputStream());
			log1.setOut(System.out);
			log1.setIndex("11");
			log1.start();
			
			System.out.println("---------Sleep1: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup1: " + new java.util.Date());

			for(int i = 0; i < 35; i++) {
				String key = "key" + (101+i);
				String value = "value" + (101+i);
				System.out.println(bftMap.putEntry("TestTable3", key, value.getBytes()));
			}
			assertEquals("Main table size should be 135", 135, bftMap.size1("TestTable3"));

			System.out.println("---------Sleep2: " + new java.util.Date());
			Thread.sleep(10000);
			System.out.println("---------Wakeup2: " + new java.util.Date());
			
			replica2.destroy(); // Killing another non-leader replica, replica3

			for(int i = 0; i < 35; i++) {
				String key = "key" + (136+i);
				String value = "value" + (136+i);
				System.out.println(bftMap.putEntry("TestTable3", key, value.getBytes()));
			}
			assertEquals("Main table size should be 170", 170, bftMap.size1("TestTable3"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		} catch(IOException ioe) {
			System.out.println("Exception when starting replica 2: " + ioe.getMessage());
		}
	}

	/**
	 * - Crate table;
	 * - Insert data;
	 * - Kills the leader replica;
	 * - Insert data;
	 * - Verify if the size of the table is correct.
	 */
	@Test
	public void testStopLeader() {
		try{
			Thread.sleep(1000);
			BFTMap bftMap = new BFTMap(1001);
			Thread.sleep(1000);
			bftMap.put("TestTable4", new HashMap<String,byte[]>());
			bftMap.putEntry("TestTable4", "key1", "value1".getBytes());
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable4"));
			
			for(int i = 0; i < 200; i++) {
				String key = "key" + (2+i);
				String value = "value" + (2+i);
				bftMap.putEntry("TestTable4", key, value.getBytes());
			}
			assertEquals("Main table size should be 201", 201, bftMap.size1("TestTable4"));

			replica0.destroy(); // Killing the leader, replica 0

			for(int i = 0; i < 200; i++) {
				String key = "key" + (202+i);
				String value = "value" + (202+i);
				bftMap.putEntry("TestTable4", key, value.getBytes());
			}
			assertEquals("Main table size should be 401", 401, bftMap.size1("TestTable4"));

		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

	
	/**
	 * - Crate table;
	 * - Insert data;
	 * - Kills the leader replica;
	 * - Insert data;
	 * - Verify if the size of the table is correct;
	 * - Start the replica again;
	 * - Insert data;
	 * - Verify if the size of the table is correct;
	 * - Kills a second leader;
	 * - Insert data;
	 * - Verify if the size of the table is correct.
	 */
	@Test
	public void testStopLeaders() {
		try{
			Thread.sleep(1000);
			BFTMap bftMap = new BFTMap(1001);
			Thread.sleep(1000);
			bftMap.put("TestTable5", new HashMap<String,byte[]>());
			
			for(int i = 0; i < 130; i++) {
				String key = "key" + (1+i);
				String value = "value" + (1+i);
				bftMap.putEntry("TestTable5", key, value.getBytes());
			}
			assertEquals("Main table size should be 130", 130, bftMap.size1("TestTable5"));

			replica0.destroy(); // Killing the leader, replica 0

			for(int i = 0; i < 60; i++) {
				String key = "key" + (131+i);
				String value = "value" + (131+i);
				bftMap.putEntry("TestTable5", key, value.getBytes());
			}
			assertEquals("Main table size should be 190", 190, bftMap.size1("TestTable5"));

			command[4] = "0";
			replica0 = new ProcessBuilder(command).redirectErrorStream(true).start(); // Starting replica0 back
			ConsoleLogger log0 = new ConsoleLogger();;
			log0.setIn(replica0.getInputStream());
			log0.setOut(System.out);
			log0.setIndex("01");
			log0.start();
			System.out.println("---------Sleep1: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup1: " + new java.util.Date());
			
			for(int i = 0; i < 20; i++) {
				String key = "key" + (191+i);
				String value = "value" + (191+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 210", 210, bftMap.size1("TestTable5"));

			System.out.println("---------Sleep2: " + new java.util.Date());
			Thread.sleep(10000);
			System.out.println("---------Wakeup2: " + new java.util.Date());
			
			replica1.destroy(); // Killing another leader replica, replica1

			for(int i = 0; i < 60; i++) {
				String key = "key" + (211+i);
				String value = "value" + (211+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 270", 270, bftMap.size1("TestTable5"));
			
			command[4] = "1";
			replica1 = new ProcessBuilder(command).redirectErrorStream(true).start(); // Starting replica0 back
			ConsoleLogger log1 = new ConsoleLogger();;
			log1.setIn(replica1.getInputStream());
			log1.setOut(System.out);
			log1.setIndex("11");
			log1.start();
			System.out.println("---------Sleep3: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup3: " + new java.util.Date());
			
			for(int i = 0; i < 20; i++) {
				String key = "key" + (271+i);
				String value = "value" + (271+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 290", 290, bftMap.size1("TestTable5"));

			System.out.println("---------Sleep4: " + new java.util.Date());
			Thread.sleep(10000);
			System.out.println("---------Wakeup4: " + new java.util.Date());
			
			replica2.destroy(); // Killing another leader replica, replica1

			for(int i = 0; i < 40; i++) {
				String key = "key" + (291+i);
				String value = "value" + (291+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 330", 330, bftMap.size1("TestTable5"));

			command[4] = "2";
			replica2 = new ProcessBuilder(command).redirectErrorStream(true).start(); // Starting replica0 back
			ConsoleLogger log2 = new ConsoleLogger();;
			log2.setIn(replica2.getInputStream());
			log2.setOut(System.out);
			log2.setIndex("21");
			log2.start();
			System.out.println("---------Sleep5: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup5: " + new java.util.Date());
			
			for(int i = 0; i < 40; i++) {
				String key = "key" + (331+i);
				String value = "value" + (331+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 370", 370, bftMap.size1("TestTable5"));

			System.out.println("---------Sleep6: " + new java.util.Date());
			Thread.sleep(10000);
			System.out.println("---------Wakeup6: " + new java.util.Date());
			
			replica3.destroy(); // Killing another leader replica, replica1

			for(int i = 0; i < 40; i++) {
				String key = "key" + (371+i);
				String value = "value" + (371+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 410", 410, bftMap.size1("TestTable5"));

			command[4] = "3";
			replica3 = new ProcessBuilder(command).redirectErrorStream(true).start(); // Starting replica0 back
			ConsoleLogger log3 = new ConsoleLogger();;
			log3.setIn(replica3.getInputStream());
			log3.setOut(System.out);
			log3.setIndex("31");
			log3.start();
			System.out.println("---------Sleep7: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup7: " + new java.util.Date());
			
			for(int i = 0; i < 20; i++) {
				String key = "key" + (411+i);
				String value = "value" + (411+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 430", 430, bftMap.size1("TestTable5"));

			System.out.println("---------Sleep8: " + new java.util.Date());
			Thread.sleep(10000);
			System.out.println("---------Wakeup8: " + new java.util.Date());
			
			replica0.destroy(); // Killing another leader replica, replica1

			for(int i = 0; i < 40; i++) {
				String key = "key" + (431+i);
				String value = "value" + (431+i);
				System.out.println(bftMap.putEntry("TestTable5", key, value.getBytes()));
			}
			assertEquals("Main table size should be 470", 470, bftMap.size1("TestTable5"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		} catch(IOException ioe) {
			System.out.println("Exception when starting replica 2: " + ioe.getMessage());
		}
	}

}
