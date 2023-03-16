package bftsmart.demo.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

public class MapServer<K, V> extends DefaultSingleRecoverable {

	private Map<K, V> replicaMap;
	private Logger logger;

	public MapServer(int id) {
		replicaMap = new TreeMap<>();
		logger = Logger.getLogger(MapServer.class.getName());
		new ServiceReplica(id, this, this);
	}

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: demo.map.MapServer <server id>");
			System.exit(-1);
		}
		new MapServer<String, String>(Integer.parseInt(args[0]));
	}

	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		try {
			MapMessage<K,V> response = new MapMessage<>();
			MapMessage<K,V> request = MapMessage.fromBytes(command);
			MapRequestType cmd = request.getType();


			switch (cmd) {
				//write operations on the map
				case PUT:
					V oldValue = replicaMap.put(request.getKey(), request.getValue());

					if (oldValue != null) {
						response.setValue(oldValue);
					}
					return MapMessage.toBytes(response);
				case REMOVE:

					V value = replicaMap.remove(request.getKey());

					if (value != null) {
						response.setValue(value);
						return MapMessage.toBytes(response);
					}
					break;
				case GET:
					V ret = replicaMap.get(request.getKey());

					if (ret != null) {
						response.setValue(ret);
					}
					return MapMessage.toBytes(response);
				case SIZE:
					int size = replicaMap.size();
					response.setSize(size);
					return MapMessage.toBytes(response);
				case KEYSET:
					response.setKeySet(replicaMap.keySet());
					return MapMessage.toBytes(response);
			}
		} catch (IOException | ClassNotFoundException ex) {
			logger.log(Level.SEVERE,"Failed to process ordered request", ex);
			return new byte[0];
		}
		return new byte[0];
	}

	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		try {
			MapMessage<K,V> response = new MapMessage<>();
			MapMessage<K,V> request = MapMessage.fromBytes(command);
			MapRequestType cmd = request.getType();

			switch (cmd) {
				//read operations on the map
				case GET:
					V ret = replicaMap.get(request.getKey());

					if (ret != null) {
						response.setValue(ret);
					}
					return MapMessage.toBytes(response);
				case SIZE:
					int size = replicaMap.size();
					response.setSize(size);
					return MapMessage.toBytes(response);
				case KEYSET:
					response.setKeySet(replicaMap.keySet());
					return MapMessage.toBytes(response);
			}
		} catch (IOException | ClassNotFoundException ex) {
			logger.log(Level.SEVERE, "Failed to process unordered request", ex);
			return new byte[0];
		}
		return new byte[0];
	}

	@Override
	public byte[] getSnapshot() {
		try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
				ObjectOutput objOut = new ObjectOutputStream(byteOut)) {
			objOut.writeObject(replicaMap);
			return byteOut.toByteArray();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Error while taking snapshot", e);
		}
		return new byte[0];
	}

	@SuppressWarnings("unchecked")
	@Override
	public void installSnapshot(byte[] state) {
		try (ByteArrayInputStream byteIn = new ByteArrayInputStream(state);
				ObjectInput objIn = new ObjectInputStream(byteIn)) {
			replicaMap = (Map<K, V>)objIn.readObject();
		} catch (IOException | ClassNotFoundException e) {
			logger.log(Level.SEVERE, "Error while installing snapshot", e);
		}
	}
}