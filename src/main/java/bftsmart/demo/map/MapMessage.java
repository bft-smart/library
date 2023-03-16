package bftsmart.demo.map;

import java.io.*;
import java.util.HashSet;
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
        objOut.writeObject(message);

        objOut.flush();
        byteOut.flush();

        return byteOut.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static <K,V> MapMessage<K,V> fromBytes(byte[] rep) throws IOException, ClassNotFoundException {
        ByteArrayInputStream byteIn = new ByteArrayInputStream(rep);
        ObjectInputStream objIn = new ObjectInputStream(byteIn);
        return (MapMessage<K,V>) objIn.readObject();
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
