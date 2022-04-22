package bftsmart.forensic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * This Class represents an audit storage
 * Contains Aggregates for each write and accept quorum created
 */
public class AuditStorage implements Serializable {

    private Map<Integer, Aggregate> writeAggregate; // consensus id to write aggregate
    private Map<Integer, Aggregate> acceptAggregate; // consensus id to accept aggregate

    public AuditStorage() {
        // System.out.println("Audit store created...");
        writeAggregate = new HashMap<>();
        acceptAggregate = new HashMap<>();
    }

    /**
     * Add write aggregate
     * 
     * @param cid consensus id
     * @param agg aggregate
     */
    public void addWriteAggregate(int cid, Aggregate agg) {
        if (writeAggregate.get(cid) == null) {
            writeAggregate.put(cid, agg);
        }
    }

    /**
     * Add accept aggregate
     * 
     * @param cid consensus id
     * @param agg aggregate
     */
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
            ret = bos.toByteArray();
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

    /**
     * Gets the minimum consensus id present
     * 
     * @return minimum consensus id
     */
    public int getMinCID() {
        int result = Integer.MAX_VALUE;
        for (int cid : writeAggregate.keySet()) {
            result = Math.min(result, cid);
        }
        for (int cid : acceptAggregate.keySet()) { // probably unecessary
            result = Math.min(result, cid);
        }
        return result;
    }

    /**
     * Gets the maximum consensus id present
     * 
     * @return maximum consensus id
     */
    public int getMaxCID() {
        int result = -1;
        for (int cid : acceptAggregate.keySet()) {
            result = Math.max(result, cid);
        }
        for (int cid : writeAggregate.keySet()) { // probably unecessary
            result = Math.max(result, cid);
        }
        return result;
    }

    /**
     * Removes unecessary proof until cid
     * 
     * @param cid last cid to remove
     */
    public void removeProofsUntil(int cid) {
        int minCid = getMinCID();
        for (int i = minCid; i <= cid; i++) {
            writeAggregate.remove(i);
            acceptAggregate.remove(i);
        }
        // System.out.println("Size of proofs = " + writeAggregate.keySet().size());
    }
}
