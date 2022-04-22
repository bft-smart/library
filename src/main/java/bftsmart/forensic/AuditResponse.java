package bftsmart.forensic;

import java.io.Serializable;
import java.util.Map;

/**
 * Unnecessary Class
 * Should be removed in the future
 */
public class AuditResponse implements Serializable {

    private AuditStorage storage;
    private int sender;

    public AuditResponse(int id, AuditStorage storage) {
        this.storage = storage;
        this.sender = id;
    }

    public AuditResponse(int id, AuditStorage storage, int lowView, int highView) {
        this.sender = id;
        this.storage = new AuditStorage();
        Map<Integer, Aggregate> writeAgg = storage.getWriteAggregate();
        Map<Integer, Aggregate> acceptAgg = storage.getAcceptAggregate();
        for (int i = lowView; i <= highView; i++) {
            if (writeAgg.get(i) != null)
                this.storage.addWriteAggregate(i, writeAgg.get(i));
            if (acceptAgg.get(i) != null)
                this.storage.addAcceptAggregate(i, acceptAgg.get(i));
        }
    }

    public AuditStorage getStorage() {
        return storage;
    }

    public int getSender(){
        return sender;
    }
}
