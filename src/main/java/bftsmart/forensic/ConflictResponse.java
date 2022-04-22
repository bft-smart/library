package bftsmart.forensic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;


/**
 * Unnecessary Class
 * Should be removed in the future
 */
public class ConflictResponse implements Serializable {

    private AuditStorage localStorage;
    private AuditStorage receivedStorage;

    public ConflictResponse(AuditStorage localStorage, AuditStorage receivedStorage) {
        this.localStorage = localStorage;
        this.receivedStorage = receivedStorage;
    }

    public byte[] toByteArray() {
        byte[] value = null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {

            oos.writeObject(this);
            oos.flush();
            value = bos.toByteArray();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return value;
    }

    public static ConflictResponse fromByteArray(byte[] value) {
        ConflictResponse result= null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(value);
        ObjectInputStream ois = new ObjectInputStream(bis)){
            result = (ConflictResponse) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public AuditStorage getLocalStorage() {
        return this.localStorage;
    }

    public void setLocalStorage(AuditStorage localStorage) {
        this.localStorage = localStorage;
    }

    public AuditStorage getReceivedStorage() {
        return this.receivedStorage;
    }

    public void setReceivedStorage(AuditStorage receivedStorage) {
        this.receivedStorage = receivedStorage;
    }

}
