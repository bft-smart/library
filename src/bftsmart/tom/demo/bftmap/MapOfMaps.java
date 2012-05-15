package bftsmart.tom.demo.bftmap;

import java.util.TreeMap;
import java.util.Map;

import java.io.Serializable;

/**
 *
 * 
 * @author sweta
 */
public class MapOfMaps implements Serializable {
	
	private static final long serialVersionUID = -8898539992606449057L;
	
	private Map<String, Map<String,byte[]>> tableMap = null;

	public MapOfMaps() {
		tableMap=new TreeMap<String, Map<String,byte[]>>();
	}

	public Map<String,byte[]> addTable(String key, Map<String, byte[]> table) {
		return tableMap.put(key, table);
	}

	public byte[] addData(String tableName, String key, byte[] value) {
		Map<String,byte[]> table = tableMap.get(tableName);
		if (table == null) { 
                    System.out.println("Non-existant table: "+tableName);
                    return null;
                }
		byte[] ret = table.put(key,value);
		return ret;
	}

	public Map<String,byte[]> getTable(String tableName) {
		return tableMap.get(tableName);
	}

	public byte[] getEntry(String tableName, String key) {
		System.out.println("Table name: "+tableName);
		System.out.println("Entry key: "+ key);
		Map<String,byte[]> info= tableMap.get(tableName);
		if (info == null) { 
                    System.out.println("Non-existant table: "+tableName);
                    return null;
                }
		return info.get(key);
	}

	public int getNumOfTables() {
		return tableMap.size();
	}

	public int getSize(String tableName) {
		Map<String,byte[]> table = tableMap.get(tableName);
		return (table == null)?0:table.size();
	}

	public Map<String,byte[]> removeTable(String tableName) {
		return tableMap.remove(tableName);
	}

	public byte[] removeEntry(String tableName,String key) {
		Map<String,byte[]> info= tableMap.get(tableName);
		return info.remove(key);
	}
}
