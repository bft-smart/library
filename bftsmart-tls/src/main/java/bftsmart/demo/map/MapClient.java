package bftsmart.demo.map;

import java.io.IOException;
import java.util.*;

import bftsmart.tom.ServiceProxy;

public class MapClient<K, V> implements Map<K, V>{
	
	ServiceProxy serviceProxy;
	
	public MapClient(int clientId) {
		serviceProxy = new ServiceProxy(clientId);
	}

	@Override
	public V put(K key, V value) {
		byte[] rep;
		try {
			MapMessage<K,V> request = new MapMessage<>();
			request.setType(MapRequestType.PUT);
			request.setKey(key);
			request.setValue(value);

			//invokes BFT-SMaRt
			rep = serviceProxy.invokeOrdered(MapMessage.toBytes(request));
		} catch (IOException e) {
			System.out.println("Failed to send PUT request: " + e.getMessage());
			return null;
		}
		if (rep.length == 0) {
			return null;
		}

		try {
			MapMessage<K,V> response = MapMessage.fromBytes(rep);
			return response.getValue();
		} catch (ClassNotFoundException | IOException ex) {
			System.out.println("Failed to deserialized response of PUT request: " + ex.getMessage());
			return null;
		}
	}

	@Override
	public V get(Object key) {
		byte[] rep;
		try {
			MapMessage<K,V> request = new MapMessage<>();
			request.setType(MapRequestType.GET);
			request.setKey(key);

			//invokes BFT-SMaRt
			rep = serviceProxy.invokeUnordered(MapMessage.toBytes(request));
		} catch (IOException e) {
			System.out.println("Failed to send GET request: " + e.getMessage());
			return null;
		}

		if (rep.length == 0) {
			return null;
		}
		try {
			MapMessage<K,V> response = MapMessage.fromBytes(rep);
			return response.getValue();
		} catch (ClassNotFoundException | IOException ex) {
			System.out.println("Failed to deserialized response of GET request: " + ex.getMessage());
			return null;
		}
	}

	@Override
	public V remove(Object key) {
		byte[] rep;
		try {
			MapMessage<K,V> request = new MapMessage<>();
			request.setType(MapRequestType.REMOVE);
			request.setKey(key);

			//invokes BFT-SMaRt
			rep = serviceProxy.invokeOrdered(MapMessage.toBytes(request));
		} catch (IOException e) {
			System.out.println("Failed to send REMOVE request: " + e.getMessage());
			return null;
		}
		if (rep.length == 0) {
			return null;
		}

		try {
			MapMessage<K,V> response = MapMessage.fromBytes(rep);
			return  response.getValue();
		} catch (ClassNotFoundException | IOException ex) {
			System.out.println("Failed to deserialized response of PUT request: " + ex.getMessage());
			return null;
		}
	}

	@Override
	public int size() {
		byte[] rep;
		try {
			MapMessage<K,V> request = new MapMessage<>();
			request.setType(MapRequestType.SIZE);

			rep = serviceProxy.invokeUnordered(MapMessage.toBytes(request));
		} catch (IOException e) {
			System.out.println("Failed to send SIZE request " + e.getMessage());
			return -1;
		}
		if (rep.length == 0) {
			return -1;
		}

		try  {
			MapMessage<K,V> response = MapMessage.fromBytes(rep);
			return response.getSize();
		} catch (IOException | ClassNotFoundException ex) {
			System.out.println("Failed to deserialized response of SIZE request: " + ex.getMessage());
			return -1;
		}
	}

	@Override
	public Set<K> keySet() {
		byte[] rep;
		try {
			MapMessage<K,V> request = new MapMessage<>();
			request.setType(MapRequestType.KEYSET);

			//invokes BFT-SMaRt
			rep = serviceProxy.invokeUnordered(MapMessage.toBytes(request));
		} catch (IOException e) {
			System.out.println("Failed to send KEYSET request: " + e.getMessage());
			return Collections.emptySet();
		}

		if (rep.length == 0) {
			return Collections.emptySet();
		}
		try {
			MapMessage<K,V> response = MapMessage.fromBytes(rep);
			return response.getKeySet();
		} catch (ClassNotFoundException | IOException ex) {
			System.out.println("Failed to deserialized response of KEYSET request: " + ex.getMessage());
			return Collections.emptySet();
		}
	}
	
	public void close() {
		serviceProxy.close();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean containsKey(Object key) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public boolean isEmpty() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	@Override
	public Collection<V> values() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}