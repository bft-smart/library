/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package bftsmart.demo.keyvalue;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

import java.io.Console;
import java.util.TreeMap;

/**
 *
 * @author sweta
 */
public class KVClient {


	public static void main(String[] args) throws IOException {
		if(args.length < 1) {
			System.out.println("Usage: java KVClient <process id> ");
			System.exit(-1);
		}

		BFTMap bftMap = new BFTMap(Integer.parseInt(args[0]));

		Console console = System.console();
		Scanner sc = new Scanner(System.in);

		while(true) {

			System.out.println("select a command : 1. CREATE A NEW TABLE OF TABLES");
			System.out.println("select a command : 2. REMOVE AN EXISTING TABLE OF TABLES");
			System.out.println("select a command : 3. GET THE SIZE OF THE TABLE OF TABLES");
			System.out.println("select a command : 4. PUT VALUES INTO A TABLE");
			System.out.println("select a command : 5. GET VALUES FROM A TABLE");
			System.out.println("select a command : 6. GET THE SIZE OF A TABLE");
			System.out.println("select a command : 7. REMOVE AN EXISTING TABLE");

			int cmd = sc.nextInt();
			
				

			switch(cmd) {
			//operations on the table
			case KVRequestType.TAB_CREATE:
				String tableName;
				boolean tableExists = false;
				do {
					tableName = console.readLine("Enter the HashMap name");
					tableExists = bftMap.containsKey(tableName);
					if (!tableExists) {
						//if the table name does not exist then create the table
						bftMap.put(tableName, new TreeMap<String,byte[]>());
					}
				} while(tableExists);
				break;

			case KVRequestType.SIZE_TABLE:
				//obtain the size of the table of tables.
				System.out.println("Computing the size of the table");
				int size=bftMap.size();
				System.out.println("The size of the table of tables is: "+size);
				break;

			case KVRequestType.TAB_REMOVE:
				//Remove the table entry
				tableExists = false;
				tableName = null;
				System.out.println("Executing remove operation on table of tables");
				do {
					tableName = console.readLine("Enter the valid table name you want to remove");
					tableExists = bftMap.containsKey(tableName);

				} while(!tableExists);
				bftMap.remove(tableName);
				System.out.println("Table removed");
				break;

				//operations on the hashmap
			case KVRequestType.PUT:
				System.out.println("Execute put function");
				String value = null;
				tableName = null;
				String index = null;
				String times = null;
					tableName = console.readLine("Enter the valid table name in which you want to enter the data");
//					tableExists = bftMap.containsKey(tableName);
//					if (tableExists) {
						//if the table name does not exist then create the table
//						index = console.readLine("Enter the index");
						times = console.readLine("Enter how many inserts");
						String byteSize = console.readLine("Enter the size of the value in bytes");
//					}

				byte[] resultBytes;
//				int init = Integer.parseInt(index);
				int total = Integer.parseInt(times);
				for(int i=0; i< total; i++) {
					String key = String.valueOf(i);
					while(key.length() < 4)
						key = "0" + key;
					Random rand = new Random();
					byte[] byteArray = new byte[Integer.parseInt(byteSize)];
					rand.nextBytes(byteArray);
					resultBytes = bftMap.putEntry(tableName, key, byteArray);
				}
				break;

			case KVRequestType.GET:
				System.out.println("Execute get function");
				tableExists = false;
				boolean keyExists = false;
				tableName = null;
				String key = null;
				do {
					tableName = console.readLine("Enter the valid table name from which you want to get the values");
					tableExists = bftMap.containsKey(tableName);
					if (tableExists) {
						do {
							key = console.readLine("Enter the valid key");
							keyExists = bftMap.containsKey1(tableName, key);
							if(keyExists) {
								resultBytes = bftMap.getEntry(tableName,key);
								System.out.println("The value received from GET is: " + new String(resultBytes));
							}

						} while(keyExists);
					}
				} while(!tableExists);
				break;

			case KVRequestType.SIZE:
				System.out.println("Execute get function");
				tableExists = false;
				tableName = null;
				size = -1;
				do {
					tableName = console.readLine("Enter the valid table whose size you want to detemine");

					tableExists = bftMap.containsKey(tableName);
					if (tableExists) {
						size = bftMap.size1(tableName);
						System.out.println("The size is: " + size);

					}
				} while(!tableExists);
				break;
			case KVRequestType.REMOVE:
				System.out.println("Execute get function");
				tableExists = false;
				keyExists = false;
				tableName = null;
				key = null;
				do {
					tableName = console.readLine("Enter the valid table name which you want to remove");

					tableExists = bftMap.containsKey(tableName);
					if (tableExists) {
						do {
							key = console.readLine("Enter the valid key");
							keyExists = bftMap.containsKey1(tableName, key);
							if(keyExists) {
								byte[] result2 = bftMap.removeEntry(tableName,key);
								System.out.println("The previous value was : "+new String(result2));
							}

						} while(!keyExists);
					}
				} while(!tableExists);
				break;

			}
		}
	}
}
