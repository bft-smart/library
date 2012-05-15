/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package bftsmart.reconfiguration;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Hashtable;
import java.util.Iterator;


/**
 *
 * @author eduardo
 */
public class ReconfigureRequest implements Externalizable{

    private int sender;
    private Hashtable<Integer,String> properties = new Hashtable<Integer,String>();
    private byte[] signature;
    
    
    public ReconfigureRequest() {
    }
    
    public ReconfigureRequest(int sender) {
        this.sender = sender;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }

    public byte[] getSignature() {
        return signature;
    }

    public Hashtable<Integer, String> getProperties() {
        return properties;
    }

    public int getSender() {
        return sender;
    }
    
    public void setProperty(int prop, String value){
        this.properties.put(prop, value);
    }
    
     @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeInt(sender);
        
        int num = properties.keySet().size();
        
        out.writeInt(num);
        
        Iterator<Integer> it = properties.keySet().iterator() ;
        
        while(it.hasNext()){
            int key = it.next();
            String value = properties.get(key);
            
            out.writeInt(key);
            out.writeUTF(value);
        }
        
        
        out.writeInt(signature.length);
        out.write(signature);
       
    }

     
    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        sender = in.readInt();
        
        int num = in.readInt();
        
        for(int i = 0; i < num; i++){
            int key = in.readInt();
            String value = in.readUTF();
            properties.put(key, value);
        }
        
        this.signature = new byte[in.readInt()];
        in.read(this.signature);
        
    }
    
    
    @Override
     public String toString(){
        String ret = "Sender :"+ sender+";";
        Iterator<Integer> it = properties.keySet().iterator() ;
        while(it.hasNext()){
            int key = it.next();
            String value = properties.get(key);
            ret = ret+key+value;
        }
        return ret;
     }
    
}
