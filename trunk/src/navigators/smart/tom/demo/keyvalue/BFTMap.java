/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo.keyvalue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.tom.ServiceProxy;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Extractor;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author sweta
 */
public class BFTMap implements Map<String, Map<String,byte[]>> {

	ServiceProxy KVProxy = null;
	BFTMap(int id) {
		KVProxy = new ServiceProxy(id, "config", new VerboseComparator(), new VerboseExtractor());
	}
	ByteArrayOutputStream out = null;

    static class VerboseComparator implements Comparator<byte[]> {
        @Override
        public int compare(byte[] o1, byte[] o2) {
            try{
                int o1v = new DataInputStream(new ByteArrayInputStream(o1)).readInt();
                int o2v = new DataInputStream(new ByteArrayInputStream(o2)).readInt();
                System.out.println(Thread.currentThread().getName()+": comparing "+o1v+" and "+o2v);
                return o1v == o2v?0:-1;
            } catch(IOException ioe) {
                return -1;
            }
        }
    }

    static class VerboseExtractor implements Extractor {
        @Override
        public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {
            System.out.print(Thread.currentThread().getName()+": Received replies = { ");

            for(TOMMessage reply:replies) {
                if(reply == null)
                    continue;

                try {
                    int v = new DataInputStream(new ByteArrayInputStream(reply.getContent())).readInt();
                    System.out.print(v+" ");
                } catch (IOException ioe) {}
            }

            System.out.println("}");
            System.out.println(Thread.currentThread().getName()+": # replies with the same content = "+sameContent);

            return replies[lastReceived];
        }

    }

	public Map<String,byte[]> get(String tableName) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.GET);
			new DataOutputStream(out).writeUTF(tableName);

			byte[] rep = KVProxy.invoke(out.toByteArray(), false);
			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
			Map<String,byte[]> table = (Map<String,byte[]>) in.readObject();
			in.close();
			return table;
		} catch (ClassNotFoundException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}

	public byte[] getEntry(String tableName,String key) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.GET);
			new DataOutputStream(out).writeUTF(tableName);
			new DataOutputStream(out).writeUTF(key);
			byte[] rep = KVProxy.invoke(out.toByteArray(), false);
			return rep;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}


	public Map<String,byte[]> put(String key, Map<String,byte[]> value) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.TAB_CREATE);
			new DataOutputStream(out).writeUTF(key);
			//ByteArrayOutputStream bos = new ByteArrayOutputStream() ;
			ObjectOutputStream  out1 = new ObjectOutputStream(out) ;
			out1.writeObject(value);
			out1.close();
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);
			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
			Map<String,byte[]> table = (Map<String,byte[]>) in.readObject();
			in.close();
			return table;

		} catch (ClassNotFoundException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}

	public byte[] putEntry(String tableName, String key, byte[] value) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.PUT);
			new DataOutputStream(out).writeUTF(tableName);
			new DataOutputStream(out).writeUTF(key);
			new DataOutputStream(out).writeUTF(new String(value));
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);
			return rep;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}

	public Map<String,byte[]> remove(Object key) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.TAB_REMOVE);
			new DataOutputStream(out).writeUTF((String) key);
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);

			ByteArrayInputStream bis = new ByteArrayInputStream(rep) ;
			ObjectInputStream in = new ObjectInputStream(bis) ;
			Map<String,byte[]> table = (Map<String,byte[]>) in.readObject();
			in.close();
			return table;
		} catch (ClassNotFoundException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}

	public byte[] removeEntry(String tableName,String key)  {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.REMOVE);
			new DataOutputStream(out).writeUTF((String) tableName);
			new DataOutputStream(out).writeUTF((String) key);
			byte[] rep = KVProxy.invoke(out.toByteArray(), false);
			return rep;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}

	}
	public int size() {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.SIZE_TABLE);
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);
			ByteArrayInputStream in = new ByteArrayInputStream(rep);
			int size = new DataInputStream(in).readInt();
			return size;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return -1;
		}
	}

	public int size1(String tableName) {
		try {
			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.SIZE);
			new DataOutputStream(out).writeUTF(tableName);
			byte[] rep = KVProxy.invoke(out.toByteArray(), false);
			ByteArrayInputStream in = new ByteArrayInputStream(rep);
			int size = new DataInputStream(in).readInt();
			return size;
		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return 0;
		}
	}

	public boolean containsKey(String key) {

		try {

			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.TAB_CREATE_CHECK);
			new DataOutputStream(out).writeUTF((String) key);
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);
			ByteArrayInputStream in = new ByteArrayInputStream(rep);
			boolean res = new DataInputStream(in).readBoolean();
			return res;

		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return false;
		}

	}

	public boolean containsKey1(String tableName, String key) {

		try {

			out = new ByteArrayOutputStream();
			new DataOutputStream(out).writeInt(KVRequestType.CHECK);
			new DataOutputStream(out).writeUTF((String) tableName);
			new DataOutputStream(out).writeUTF((String) key);
			byte[] rep = KVProxy.invoke(out.toByteArray(),false);
			ByteArrayInputStream in = new ByteArrayInputStream(rep);
			boolean res = new DataInputStream(in).readBoolean();
			return res;

		} catch (IOException ex) {
			Logger.getLogger(BFTMap.class.getName()).log(Level.SEVERE, null, ex);
			return false;
		}

	}




	public boolean isEmpty() {
		throw new UnsupportedOperationException("Not supported yet.");
	}



	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public void putAll(Map m) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public void clear() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Set keySet() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Collection values() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Set entrySet() {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public boolean containsKey(Object key) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public HashMap<String, byte[]> get(Object key) {
		throw new UnsupportedOperationException("Not supported yet.");
	}


}


