/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.demo.simplekv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 *
 * @author alyssonbessani
 */
public class KVMessage {
    public enum Type {PUT, REMOVE, GET, SIZE, ERROR};
    
    public Type type;
    public String key;
    public String value;
    
    public KVMessage(Type type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }
    
    public byte[] getBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeUTF(type.name());
            dos.writeUTF(key);
            dos.writeUTF(value);
            dos.flush();
            return out.toByteArray();
        } catch (IOException ex) {
            return null;
        }
    }
    
    public static KVMessage createFromBytes(byte[] bytes) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(bytes);
            DataInputStream dis = new DataInputStream(in);
            return new KVMessage(Type.valueOf(dis.readUTF()),dis.readUTF(),dis.readUTF());
        } catch (IOException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "("+type+","+key+","+value+")";
    }
}