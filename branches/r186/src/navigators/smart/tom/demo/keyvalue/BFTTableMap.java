/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo.keyvalue;
import java.util.HashMap;
import java.util.Map;

import java.io.Serializable;

/**
 *
 * @author sweta
 */
public class BFTTableMap implements Serializable {
	
	private static final long serialVersionUID = -8898539992606449057L;
	
	private Map<String, Map<String,byte[]>> tableMap = null;

	public BFTTableMap() {
		tableMap=new HashMap<String, Map<String,byte[]>>();
	}

	public Map<String,byte[]> addTable(String key, Map<String, byte[]> table) {
		return tableMap.put(key, table);
	}

	public byte[] addData(String tableName, String key, byte[] value) {
		Map<String,byte[]> table = tableMap.get(tableName);
		if (table == null) System.out.println("This is null");
		byte[] ret = table.put(key,value);
		return ret;
	}

	public Map<String,byte[]> getName(String tableName) {
		return tableMap.get(tableName);
	}

	public byte[] getEntry(String tableName, String key) {
		System.out.println("Table name: "+tableName);
		System.out.println("Entry key: "+ key);
		Map<String,byte[]> info= tableMap.get(tableName);
		System.out.println("Table: "+info);
		return info.get(key);
	}

	public int getSizeofTable() {

		return tableMap.size();

	}

	public int getSize(String tableName) {
		Map<String,byte[]> table = tableMap.get(tableName);
		return table.size();
	}

	public Map<String,byte[]> removeTable(String tableName) {
		return tableMap.remove(tableName);
	}

	public byte[] removeEntry(String tableName,String key) {
		Map<String,byte[]> info= tableMap.get(tableName);
		return info.remove(key);
	}
}
