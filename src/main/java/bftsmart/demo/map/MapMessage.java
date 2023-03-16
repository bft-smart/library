package bftsmart.demo.map;

import java.io.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapMessage<K,V> implements Serializable {
    private MapRequestType type;
    private K key;
    private V value;
    private HashSet<K> keySet;
    private int size;

    public MapMessage() {
    }

    public static <K,V> byte[] toBytes(MapMessage<K,V> message) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        ObjectOutputStream objOut = new ObjectOutputStream(byteOut);
        boolean hasType = message.getType() != null;
        objOut.writeBoolean(hasType);
        if(hasType) {
            objOut.writeObject(message.getType());
        }
        boolean hasKey = message.getKey() != null;
        objOut.writeBoolean(hasKey);
        if(hasKey) {
            objOut.writeObject(message.getKey());
        }
        boolean hasValue = message.getValue() != null;
        objOut.writeBoolean(hasValue);
        if(hasValue) {
            objOut.writeObject(message.getValue());
        }
        objOut.writeInt(message.getSize());
        boolean hasKeySet = message.getKeySet() != null;
        objOut.writeBoolean(hasKeySet);
        if(message.getKeySet() != null) {
            Set<K> keySet = message.getKeySet();
            int size = keySet.size();
            objOut.writeInt(size);
            for (K key : keySet)
                objOut.writeObject(key);
        }


        objOut.flush();
        byteOut.flush();

        return byteOut.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static <K,V> MapMessage<K,V> fromBytes(byte[] rep) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(rep);
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        MapMessage<K,V> msg = new MapMessage<>();
        boolean hasType = objIn.readBoolean();
        if(hasType) {
            msg.setType((MapRequestType) objIn.readObject());
        }
        boolean hasKey = objIn.readBoolean();
        if(hasKey) {
            msg.setKey(objIn.readObject());
        }
        boolean hasValue = objIn.readBoolean();
        if(hasValue) {
            msg.setValue(objIn.readObject());
        }
        msg.setSize(objIn.readInt());
        boolean hasKeySet = objIn.readBoolean();
        if(hasKeySet) {
            int setSize = objIn.readInt();
            Set<K> result = new HashSet<>();
            while (setSize-- > 0) {
                result.add((K)objIn.readObject());
            }
            msg.setKeySet(result);
        }

        return msg;
    }

    public MapRequestType getType() {
        return type;
    }

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public void setKey(Object key) {
        this.key = (K) key;
    }

    @SuppressWarnings("unchecked")
    public void setValue(Object value) {
        this.value = (V) value;
    }

    @SuppressWarnings("unchecked")
    public void setKeySet(Object keySet) {
        this.keySet = new HashSet<>((Set<K>) keySet);
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setType(MapRequestType type) {
        this.type = type;
    }

    public Set<K> getKeySet() {
        return keySet;
    }

    public int getSize() {
        return size;
    }
}
