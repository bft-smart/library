/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo.bftmap;

import java.io.IOException;
import java.util.TreeMap;
import java.util.Random;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;




public class BFTMapClientCloudFIT
{

	public static Logger logger;

	
	public static void main(String[] args) throws NumberFormatException
	{
		if(args.length < 4)
		{
			System.out.println("Usage: java KVClients <number clients> <process id base> <use readonly?> <time for running (sec)");
			System.exit(-1);
		}
		
		//get arguments 
		int numberClients = Integer.parseInt(args[0]);
		int idBase = Integer.parseInt(args[1]);
		boolean readOnly = Boolean.parseBoolean(args[2]);
		int timeSec = Integer.parseInt(args[3]);
		
		//init log
		initLog();
		
		//create the KVClient threads
		KVClientInstance [] list = new KVClientInstance[numberClients]; 
		
		for(int i = 0 ; i < list.length ; i ++)
		{
				list[i] = new KVClientInstance(idBase + i,readOnly);
				list[i].start();
		}
		
		//leave the threads processing for a while
		try
		{
			Thread.sleep(timeSec * 1000);			
		} catch (InterruptedException e) {}
		
		System.out.println("Stop running...");
		
		
		//stop and wait for the threads
		for(int i = 0 ; i < list.length ; i ++)
		{
				list[i].stopRun();
				try {
					list[i].join();
				} catch (InterruptedException e) {}
		}
		
		System.out.println("Test ended...exit!");
		System.exit(0);
	}	
	
	private static void initLog()
	{
	    try {
	      boolean append = true;
	      FileHandler fh = new FileHandler(BFTMapClientCloudFIT.class.getName()+".log", append);
	      fh.setFormatter(new SimpleFormatter());
	      
	      logger = Logger.getLogger(BFTMapClientCloudFIT.class.getName());
	      logger.addHandler(fh);
	    }
	    catch (IOException e) {
	    	System.out.println("PROBLEMS]: "+e.getMessage());
			System.exit(-1);
	    }
	}
}
class KVClientInstance extends Thread{
	
	private int inc;
	private int id;
	private boolean readOnly;
	private boolean run;
	private Random rand;
	
	public KVClientInstance(int id,boolean readOnly)
	{
		this.id = id;
		this.readOnly = readOnly;
		this.inc = 0;
		this.run = true;
		this.rand = new Random(id);
	}
	
	public void run()
	{

		
		BFTMap bftMap = new BFTMap(id,readOnly);
		String tableName = "table-"+id;

		try {
			createTable(bftMap,tableName);
		} catch (Exception e1) {
			System.out.println("Problems: Inserting a new value into the table("+tableName+"): "+e1.getLocalizedMessage());
			System.exit(1);	
		}

		while(run)
		{
			try {
				
				boolean result = insertValue(bftMap,tableName);
				if(!result)
				{
					System.out.println("Problems: Inserting a new value into the table("+tableName+")");
					System.exit(1);	
				}

			} catch (InterruptedException e) {
				System.out.println("Client id["+id+"]: it was interrupted");
				run = false;
			} catch (Exception e) {
				bftMap = new BFTMap(id,readOnly);
				try {
					createTable(bftMap,tableName);
				} catch (Exception e1) {
					System.out.println("Client id["+id+"]: problems");
				}
			}
		}
		
		BFTMapClientCloudFIT.logger.info("Client id["+id+"] operations: "+inc);
		return;
	}
	
	public void stopRun()
	{
		this.run = false;
	}
	
	private boolean createTable(BFTMap bftMap, String nameTable) throws Exception
	{
		boolean tableExists;

		tableExists = bftMap.containsKey(nameTable);
		if (tableExists == false)
			bftMap.put(nameTable, new TreeMap<String,byte[]>());

		return tableExists;
	}

	private boolean insertValue(BFTMap bftMap, String nameTable) throws Exception
	{
		String key = "Key"+rand.nextInt();
		String value = Integer.toString(new Random().nextInt());
		byte[] valueBytes = value.getBytes();

		byte[] resultBytes = bftMap.putEntry(nameTable, key, valueBytes);
		if(resultBytes== null)
			throw new Exception();
		else
			inc++;

		return true;
	}
}