/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo.keyvalue;
import java.io.IOException;
import java.util.Scanner;

import java.io.Console;
import java.util.TreeMap;
import java.util.Random;

/**
 *
 * @author sweta
 */
/*public class KVClient {


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
						bftMap.put(tableName, new HashMap<String,byte[]>());
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

				} while(tableExists);
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
						index = console.readLine("Enter the index");
						times = console.readLine("Enter how many inserts");
						value = console.readLine("Enter the value");
//					}

				byte[] resultBytes;
				int init = Integer.parseInt(index);
				int total = Integer.parseInt(times);
				for(int i=0; i< total; i++) {
				byte[] valueBytes = value.getBytes();
				String key = "Key" + (i+init);
				resultBytes = bftMap.putEntry(tableName, key, valueBytes);
				System.out.println("Result : "+new String(resultBytes));
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
}*/
public class KVClient
{
	static int inc = 0;

	public static void main(String[] args){
		if(args.length < 2)
		{
			System.out.println("Usage: java KVClient <process id> <use readonly?>");
			System.exit(-1);
		}

		int idProcess = Integer.parseInt(args[0]);//get process id

		BFTMap bftMap = new BFTMap(idProcess, Boolean.parseBoolean(args[1]));
		String tableName = "table-"+idProcess;

		try {
			createTable(bftMap,tableName);
		} catch (Exception e1) {
			System.out.println("Problems: Inserting a new value into the table("+tableName+"): "+e1.getLocalizedMessage());
			System.exit(1);	
		}

		while(true)
		{
			try {
				boolean result = insertValue(bftMap,tableName);
				if(!result)
				{
					System.out.println("Problems: Inserting a new value into the table("+tableName+")");
					System.exit(1);	
				}

				int sizeTable = getSizeTable(bftMap, tableName);
				
				System.out.println("Size of the table("+tableName+"): "+sizeTable);
			} catch (Exception e) {
				e.printStackTrace();
//				bftMap = new BFTMap(idProcess, Boolean.parseBoolean(args[1]));
//				try {
//					createTable(bftMap,tableName);
//				} catch (Exception e1) {
//					System.out.println("problems :-(");
//				}
			}
		}
	}

	private static boolean createTable(BFTMap bftMap, String nameTable) throws Exception
	{
		boolean tableExists;

		tableExists = bftMap.containsKey(nameTable);
		if (tableExists == false)
			bftMap.put(nameTable, new TreeMap<String,byte[]>());

		return tableExists;
	}

	private static boolean insertValue(BFTMap bftMap, String nameTable) throws Exception
	{

		String key = "Key" + (inc++);
		String value = Integer.toString(new Random().nextInt());
		byte[] valueBytes = value.getBytes();

		byte[] resultBytes = bftMap.putEntry(nameTable, key, valueBytes);
		if(resultBytes== null)
			throw new Exception();
		System.out.println("Result : "+new String(resultBytes));

		return true;
	}

	private static int getSizeTable(BFTMap bftMap, String tableName) throws Exception
	{
		int res = bftMap.size1(tableName);
		if(res == -1)
			throw new Exception();
		return  res;
	}

}