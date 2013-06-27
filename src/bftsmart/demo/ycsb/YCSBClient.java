package bftsmart.demo.ycsb;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import bftsmart.tom.ServiceProxy;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;

public class YCSBClient extends DB {

	private static AtomicInteger counter = new AtomicInteger();
	private ServiceProxy proxy = null;
	private int myId;
	
	public YCSBClient() {
	}
	
	@Override
	public void init() {
		Properties props = getProperties();
		int initId = Integer.valueOf((String)props.get("smart-initkey"));
		myId = initId + counter.addAndGet(1);
		proxy = new ServiceProxy(myId);
		System.out.println("YCSBKVClient. Initiated client id: " + myId);
	}

	@Override
	public int delete(String arg0, String arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int insert(String table, String key,
			HashMap<String, ByteIterator> values) {
		
		Iterator<String> keys = values.keySet().iterator();
		HashMap<String, byte[]> map = new HashMap<String, byte[]>();
		while(keys.hasNext()) {
			String field = keys.next();
			map.put(field, values.get(field).toArray());
		}
		YCSBMessage msg = YCSBMessage.newInsertRequest(table, key, map);
		byte[] reply = proxy.invokeOrdered(msg.getBytes());
		YCSBMessage replyMsg = YCSBMessage.getObject(reply);
		return replyMsg.getResult();
	}

	@Override
	public int read(String table, String key,
			Set<String> fields, HashMap<String, ByteIterator> result) {
		HashMap<String, byte[]> results = new HashMap<String, byte[]>();
		YCSBMessage request = YCSBMessage.newReadRequest(table, key, fields, results);
		byte[] reply = proxy.invokeUnordered(request.getBytes());
		YCSBMessage replyMsg = YCSBMessage.getObject(reply);
		return replyMsg.getResult();
	}

	@Override
	public int scan(String arg0, String arg1, int arg2, Set<String> arg3,
			Vector<HashMap<String, ByteIterator>> arg4) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(String table, String key,
			HashMap<String, ByteIterator> values) {
		Iterator<String> keys = values.keySet().iterator();
		HashMap<String, byte[]> map = new HashMap<String, byte[]>();
		while(keys.hasNext()) {
			String field = keys.next();
			map.put(field, values.get(field).toArray());
		}
		YCSBMessage msg = YCSBMessage.newUpdateRequest(table, key, map);
		byte[] reply = proxy.invokeOrdered(msg.getBytes());
		YCSBMessage replyMsg = YCSBMessage.getObject(reply);
		return replyMsg.getResult();
	}

}
