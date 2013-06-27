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

import java.util.HashMap;

import org.junit.Test;

import bftsmart.demo.bftmap.BFTMap;
import bftsmart.reconfiguration.StatusReply;
import bftsmart.ReplicaStatusLogger;
import bftsmart.TestFixture;

/**
 * 
 * @author Marcel Santos
 *
 */
public class BFTMapClientTest extends TestFixture {

	private BFTMap bftMap;
	
	private void insert(String tableName, int entries) {
		int index = bftMap.size1(tableName);
		for(int i = 0; i < entries; i++) {
			String key = "key" + (i + index);
			String value = "value" + (i + index);
			bftMap.putEntry(tableName, key, value.getBytes());
		}
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
			bftMap = new BFTMap(1001);
			bftMap.put("TestTable1", new HashMap<String,byte[]>());
			bftMap.putEntry("TestTable1", "key0", "value0".getBytes());
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable1"));
			
			insert("TestTable1", 100);
			
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
			bftMap = new BFTMap(1001);
			bftMap.put("TestTable2", new HashMap<String,byte[]>());
			insert("TestTable2", 1);
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable2"));
			
			insert("TestTable2", 200);
			assertEquals("Main table size should be 201", 201, bftMap.size1("TestTable2"));

			stopServer(1);
			insert("TestTable2", 200);
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
			bftMap = new BFTMap(1001);
			Thread.sleep(1000);
			bftMap.put("TestTable3", new HashMap<String,byte[]>());
			
			insert("TestTable3", 65);
			assertEquals("Main table size should be 65", 65, bftMap.size1("TestTable3"));

			stopServer(1);
			insert("TestTable3", 35);
			assertEquals("Main table size should be 100", 100, bftMap.size1("TestTable3"));
			
			startServer(1);
			
			Thread.sleep(10000);

			insert("TestTable3", 35);
			assertEquals("Main table size should be 135", 135, bftMap.size1("TestTable3"));

			Thread.sleep(10000);
			
			stopServer(2);
			
			insert("TestTable3", 35);
			assertEquals("Main table size should be 170", 170, bftMap.size1("TestTable3"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
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
			bftMap = new BFTMap(1001);
			Thread.sleep(1000);
			bftMap.put("TestTable4", new HashMap<String,byte[]>());
			insert("TestTable4", 1);
			assertEquals("Main table size should be 1", 1, bftMap.size1("TestTable4"));
			
			insert("TestTable4", 200);
			assertEquals("Main table size should be 201", 201, bftMap.size1("TestTable4"));

			ReplicaStatusLogger statusLogger = new ReplicaStatusLogger(1);
			
			stopServer(0);

			Thread.sleep(1000);

			insert("TestTable4", 200);
			assertEquals("Main table size should be 401", 401, bftMap.size1("TestTable4"));
			
			statusLogger.interrupt();

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
			bftMap = new BFTMap(1001);
			bftMap.put("TestTable5", new HashMap<String,byte[]>());
			
			insert("TestTable5", 130);
			assertEquals("Main table size should be 130", 130, bftMap.size1("TestTable5"));

			stopServer(0);

			insert("TestTable5", 60);
			assertEquals("Main table size should be 190", 190, bftMap.size1("TestTable5"));

			startServer(0);
			
			StatusReply reply = askStatus(0);
			while(reply != StatusReply.READY) {
				Thread.sleep(5000);
				reply = askStatus(0);
			}
			
			insert("TestTable5", 20);
			assertEquals("Main table size should be 210", 210, bftMap.size1("TestTable5"));

			Thread.sleep(2000);
			
			reply = askStatus(0);
			while(reply != StatusReply.READY) {
				Thread.sleep(5000);
				reply = askStatus(0);
			}

			stopServer(1);

			insert("TestTable5", 60);
			assertEquals("Main table size should be 270", 270, bftMap.size1("TestTable5"));
			
			startServer(1);
			reply = askStatus(1);
			while(reply != StatusReply.READY) {
				Thread.sleep(5000);
				reply = askStatus(1);
			}
			
			insert("TestTable5", 20);
			assertEquals("Main table size should be 290", 290, bftMap.size1("TestTable5"));
			
			stopServer(2);

			insert("TestTable5", 40);
			assertEquals("Main table size should be 330", 330, bftMap.size1("TestTable5"));

			startServer(2);
			reply = askStatus(2);
			while(reply != StatusReply.READY) {
				Thread.sleep(5000);
				reply = askStatus(2);
			}
			
			insert("TestTable5", 40);
			assertEquals("Main table size should be 370", 370, bftMap.size1("TestTable5"));

			stopServer(3);
			
			insert("TestTable5", 40);
			assertEquals("Main table size should be 410", 410, bftMap.size1("TestTable5"));

			startServer(3);
			reply = askStatus(3);
			while(reply != StatusReply.READY) {
				Thread.sleep(5000);
				reply = askStatus(3);
			}
			
			insert("TestTable5", 20);
			assertEquals("Main table size should be 430", 430, bftMap.size1("TestTable5"));

			stopServer(0);
			
			insert("TestTable5", 40);
			assertEquals("Main table size should be 470", 470, bftMap.size1("TestTable5"));
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}

}
