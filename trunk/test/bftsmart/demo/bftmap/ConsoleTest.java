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
package bftsmart.demo.bftmap;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;


import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import bftsmart.ConsoleLogger;
import bftsmart.demo.bftmap.BFTMap;

/**
 * 
 * @author Marcel Santos
 *
 */
public class ConsoleTest {

	// Comment to test SVN branch creation left by Marcel
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
			command[2] = "bin/SMaRt.jar:lib/slf4j-api-1.5.8.jar:lib/slf4j-jdk14-1.5.8.jar:lib/netty-3.1.1.GA.jar:lib/commons-codec-1.5.jar";
			command[3] = "bftsmart.demo.keyvalue.BFTMapImpl";
			command[4] = "0";
			replica0 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log0 = new ConsoleLogger();
			log0.setIndex("0");
			log0.setIn(replica0.getInputStream());
			log0.setOut(System.out);
			log0.start();

			command[4] = "1";
			replica1 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log1 = new ConsoleLogger();
			log1.setIndex("1");
			log1.setIn(replica1.getInputStream());
			log1.setOut(System.out);
			log1.start();

			command[4] = "2";
			replica2 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log2 = new ConsoleLogger();
			log2.setIndex("2");
			log2.setIn(replica2.getInputStream());
			log2.setOut(System.out);
			log2.start();

			command[4] = "3";
			replica3 = new ProcessBuilder(command).redirectErrorStream(true).start();
			ConsoleLogger log3 = new ConsoleLogger();
			log3.setIndex("3");
			log3.setIn(replica3.getInputStream());
			log3.setOut(System.out);
			log3.start();

			System.out.println("Servers started");

		} catch(IOException ioe) {
			System.out.println("Exception during BFTMapInteractiveClient test: ");
			System.out.println(ioe.getMessage());
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

	public static void main(String[] args) {
		ConsoleTest test = new ConsoleTest();
		startServers();
		test.testStopAndStartNonLeader();
		try {
		stopServers();
		} catch(Exception e) {
			e.printStackTrace();
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
			Thread.sleep(1000);
			BFTMap bftMap = new BFTMap(1001);
			bftMap.put("TestTable2", new HashMap<String,byte[]>());
			
			for(int i = 0; i < 65; i++) {
				String key = "key" + (1+i);
				String value = "value" + (2+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}

			System.out.println("---------Sleep1: " + new java.util.Date());
			Thread.sleep(5000);
			System.out.println("---------Wakeup1: " + new java.util.Date());

			System.out.println("---------Killing replica 1");
			replica1.destroy(); // Killing a non-leader replica, replica1
			
			System.out.println("---------Sleep2: " + new java.util.Date());
			Thread.sleep(5000);
			System.out.println("---------Wakeup2: " + new java.util.Date());

			for(int i = 0; i < 35; i++) {
				String key = "key" + (66+i);
				String value = "value" + (66+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}

			System.out.println("---------Sleep3: " + new java.util.Date());
			Thread.sleep(5000);
			System.out.println("---------Wakeup3: " + new java.util.Date());

			command[4] = "1";
			System.out.println("---------Starting replica 1");
			replica1 = new ProcessBuilder(command).start(); // Starting replica1 back
			ConsoleLogger log1 = new ConsoleLogger();
			log1.setIndex("11");
			log1.setIn(replica1.getInputStream());
			log1.setOut(System.out);
			log1.start();
			
			System.out.println("---------Sleep4: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup4: " + new java.util.Date());

			for(int i = 0; i < 35; i++) {
				String key = "key" + (101+i);
				String value = "value" + (101+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}
			
			System.out.println("---------Sleep2: " + new java.util.Date());
			Thread.sleep(20000);
			System.out.println("---------Wakeup2: " + new java.util.Date());
			
			replica2.destroy(); // Killing another non-leader replica, replica3
			for(int i = 0; i < 35; i++) {
				String key = "key" + (136+i);
				String value = "value" + (136+i);
				bftMap.putEntry("TestTable2", key, value.getBytes());
			}
			System.out.println("----------Table size:" + bftMap.size1("TestTable2"));
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		} catch(IOException ioe) {
			System.out.println("Exception when starting replica 2: " + ioe.getMessage());
		}
	}

}
