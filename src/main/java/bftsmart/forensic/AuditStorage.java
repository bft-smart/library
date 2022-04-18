package bftsmart.forensic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class AuditStorage implements Serializable {

    private Map<Integer, Aggregate> writeAggregate; // consensus id to write aggregate
    private Map<Integer, Aggregate> acceptAggregate; // consensus id to accept aggregate

    public AuditStorage() {
        //System.out.println("Audit store created...");
        writeAggregate = new HashMap<>();
        acceptAggregate = new HashMap<>();
    }

    public void addWriteAggregate(int cid, Aggregate agg) {
        if (writeAggregate.get(cid) == null) {
            writeAggregate.put(cid, agg);
        }
    }

    public void addAcceptAggregate(int cid, Aggregate agg) {
        if (acceptAggregate.get(cid) == null) {
            acceptAggregate.put(cid, agg);
        }
    }

    public Map<Integer, Aggregate> getAcceptAggregate() {
        return acceptAggregate;
    }

    public Map<Integer, Aggregate> getWriteAggregate() {
        return writeAggregate;
    }

    public byte[] toByteArray() {
        byte[] ret = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            oos.flush();
            ret =  bos.toByteArray();
        } catch (Exception e) {
            System.out.println("Error serializing storage");
        }
        return ret;
    }

    public static AuditStorage fromByteArray(byte[] value) {
        AuditStorage ret = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
                ObjectInputStream ois = new ObjectInputStream(bis)) {
            ret = (AuditStorage) ois.readObject();
        } catch (Exception e) {
            System.out.println("Error deserializing storage");
            e.printStackTrace();
        }
        return ret;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder("\nBank state:\n");
        for (Integer id : writeAggregate.keySet()) {
            builder.append("\nConsensus id = " + id + "\n" + writeAggregate.get(id).toString());
        }
        for (Integer id : acceptAggregate.keySet()) {
            builder.append("\nConsensus id = " + id + "\n" + acceptAggregate.get(id).toString());
        }
        return builder.toString();
    }
}
