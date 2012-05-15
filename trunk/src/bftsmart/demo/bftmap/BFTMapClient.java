package bftsmart.demo.bftmap;
import java.util.TreeMap;
import java.util.Random;

public class BFTMapClient
{
	static int inc = 0;

	public static void main(String[] args){
		if(args.length < 2)
		{
			System.out.println("Usage: java BFTMapClient <process id> <use readonly?>");
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
		
		while(true)	{
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

	private static boolean createTable(BFTMap bftMap, String nameTable) throws Exception {
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