package bftsmart.demo.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import bftsmart.tom.ServiceProxy;

public class MapClient<K, V> implements Map<K, V>{
	
	ServiceProxy serviceProxy;
	
	public MapClient(int clientId) {
		serviceProxy = new ServiceProxy(clientId);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V put(K key, V value) {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			
			objOut.writeObject(MapRequestType.PUT);
			objOut.writeObject(key);
			objOut.writeObject(value);
			
			objOut.flush();
			byteOut.flush();
			
			byte[] reply = serviceProxy.invokeOrdered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
					ObjectInput objIn = new ObjectInputStream(byteIn)) {
				return (V)objIn.readObject();
			}
				
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception putting value into map: " + e.getMessage());
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			
			objOut.writeObject(MapRequestType.GET);
			objOut.writeObject(key);
			
			objOut.flush();
			byteOut.flush();
			
			byte[] reply = serviceProxy.invokeUnordered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
					ObjectInput objIn = new ObjectInputStream(byteIn)) {
				return (V)objIn.readObject();
			}
				
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception getting value from map: " + e.getMessage());
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V remove(Object key) {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			
			objOut.writeObject(MapRequestType.REMOVE);
			objOut.writeObject(key);
			
			objOut.flush();
			byteOut.flush();
			
			byte[] reply = serviceProxy.invokeOrdered(byteOut.toByteArray());
			if (reply.length == 0)
				return null;
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
					ObjectInput objIn = new ObjectInputStream(byteIn)) {
				return (V)objIn.readObject();
			}
				
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception removing value from map: " + e.getMessage());
		}
		return null;
	}

	@Override
	public int size() {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			objOut.writeObject(MapRequestType.SIZE);
			objOut.flush();
			byteOut.flush();
			
			byte[] reply = serviceProxy.invokeUnordered(byteOut.toByteArray());
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
					ObjectInput objIn = new ObjectInputStream(byteIn)) {
				return objIn.readInt();
			}
				
		} catch (IOException e) {
			System.out.println("Exception reading size of map: " + e.getMessage());
		}
		return -1;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Set<K> keySet() {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut);) {
			
			objOut.writeObject(MapRequestType.KEYSET);
			
			objOut.flush();
			byteOut.flush();
			
			byte[] reply = serviceProxy.invokeUnordered(byteOut.toByteArray());
			try (ByteArrayInputStream byteIn = new ByteArrayInputStream(reply);
					ObjectInput objIn = new ObjectInputStream(byteIn)) {
				int size = objIn.readInt();
				Set<K> result = new HashSet<>();
				while (size-- > 0) {
					result.add((K)objIn.readObject());
				}
				return result;
			}
				
		} catch (IOException | ClassNotFoundException e) {
			System.out.println("Exception getting keyset from map: " + e.getMessage());
		}
		return null;
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