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
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.server.SingleExecutable;
import navigators.smart.tom.server.Recoverable;

/**
 *
 * @author sweta
 * 
 * This class will create a ServiceReplica and will initialize
 * it with a implementation of Executable and Recoverable interfaces. 
 */
public class BFTMapImpl implements SingleExecutable, Recoverable {

    BFTTableMap tableMap = new BFTTableMap();
    ServiceReplica replica = null;
    
    //The constructor passes the id of the server to the super class
    public BFTMapImpl(int id) {
    	replica = new ServiceReplica(id, this, this);
    }

    public static void main(String[] args){
        if(args.length < 1) {
            System.out.println("Use: java BFTMapImpl <processId>");
            System.exit(-1);
        }
        new BFTMapImpl(Integer.parseInt(args[0]));
    }
    
    @Override
    public byte[] getState() {
        try {

            // serialize to byte array and return
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(tableMap);
            
            out.flush();
            bos.flush();
            out.close();
            bos.close();
            return bos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
            return new byte[0];
        }
    }

    @Override
    public void setState(byte[] state) {
        try {

            // serialize to byte array and return
            ByteArrayInputStream bis = new ByteArrayInputStream(state);
            ObjectInput in = new ObjectInputStream(bis);
            tableMap = (BFTTableMap) in.readObject();
            in.close();
            bis.close();

        } catch (ClassNotFoundException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    @SuppressWarnings("static-access")
    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        try {
            ByteArrayInputStream in = new ByteArrayInputStream(command);
            ByteArrayOutputStream out = null;
            byte[] reply = null;
            int cmd = new DataInputStream(in).readInt();
            switch (cmd) {
                //operations on the hashmap
                case KVRequestType.PUT:
                    String tableName = new DataInputStream(in).readUTF();
                    String key = new DataInputStream(in).readUTF();
                    String value = new DataInputStream(in).readUTF();
                    byte[] valueBytes = value.getBytes();
                    System.out.println("Key received: " + key);
                    byte[] ret = tableMap.addData(tableName, key, valueBytes);
                    if (ret == null) {
//                        System.out.println("Return is null, so there was no data before");
                        ret = new byte[0];
                    }
                    reply = valueBytes;
                    break;
                case KVRequestType.REMOVE:
                    tableName = new DataInputStream(in).readUTF();
                    key = new DataInputStream(in).readUTF();
                    System.out.println("Key received: " + key);
                    valueBytes = tableMap.removeEntry(tableName, key);
                    value = new String(valueBytes);
                    System.out.println("Value removed is : " + value);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(value);
                    reply = out.toByteArray();
                    break;
                case KVRequestType.TAB_CREATE:
                    tableName = new DataInputStream(in).readUTF();
                    //ByteArrayInputStream in1 = new ByteArrayInputStream(command);
                    ObjectInputStream objIn = new ObjectInputStream(in);
                    Map table = null;
                    try {
                        table = (Map<String, byte[]>) objIn.readObject();
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    Map<String, byte[]> tableCreated = tableMap.addTable(tableName, table);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream objOut = new ObjectOutputStream(bos);
                    objOut.writeObject(tableCreated);
                    objOut.close();
                    in.close();
                    reply = bos.toByteArray();
                    break;
                case KVRequestType.TAB_REMOVE:
                    tableName = new DataInputStream(in).readUTF();
                    table = tableMap.removeTable(tableName);
                    bos = new ByteArrayOutputStream();
                    objOut = new ObjectOutputStream(bos);
                    objOut.writeObject(table);
                    objOut.close();
                    objOut.close();
                    reply = bos.toByteArray();
                    break;

            
                case KVRequestType.SIZE_TABLE:
                    int size1 = tableMap.getSizeofTable();
                    System.out.println("Size " + size1);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size1);
                    reply = out.toByteArray();
                    break;
                case KVRequestType.GET:
                    tableName = new DataInputStream(in).readUTF();
                    System.out.println("tablename: " + tableName);
                    key = new DataInputStream(in).readUTF();
                    System.out.println("Key received: " + key);
                    valueBytes = tableMap.getEntry(tableName, key);
                    value = new String(valueBytes);
                    System.out.println("The value to be get is: " + value);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(value);
                    reply = out.toByteArray();
                    break;
                case KVRequestType.SIZE:
                    String tableName2 = new DataInputStream(in).readUTF();
                    int size = tableMap.getSize(tableName2);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size);
                    reply = out.toByteArray();
                    break;
	            case KVRequestType.CHECK:
	                tableName = new DataInputStream(in).readUTF();
	                key = new DataInputStream(in).readUTF();
	                System.out.println("Table Key received: " + key);
	                valueBytes = tableMap.getEntry(tableName, key);
	                boolean entryExists = valueBytes != null;
	                out = new ByteArrayOutputStream();
	                new DataOutputStream(out).writeBoolean(entryExists);
	                reply = out.toByteArray();
	                break;
			    case KVRequestType.TAB_CREATE_CHECK:
			        tableName = new DataInputStream(in).readUTF();
			        System.out.println("Table of Table Key received: " + tableName);
			        table = tableMap.getName(tableName);
			        boolean tableExists = (table != null);
			        System.out.println("Table exists: " + tableExists);
			        out = new ByteArrayOutputStream();
			        new DataOutputStream(out).writeBoolean(tableExists);
			        reply = out.toByteArray();
			        break;
            }
            return reply;
        } catch (IOException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @SuppressWarnings("static-access")
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
    	try {
	        ByteArrayInputStream in = new ByteArrayInputStream(command);
	        ByteArrayOutputStream out = null;
	        byte[] reply = null;
	        int cmd = new DataInputStream(in).readInt();
	        switch (cmd) {
                case KVRequestType.SIZE_TABLE:
                    int size1 = tableMap.getSizeofTable();
                    System.out.println("Size " + size1);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size1);
                    reply = out.toByteArray();
                    break;
                case KVRequestType.GET:
                    String tableName = new DataInputStream(in).readUTF();
                    System.out.println("tablename: " + tableName);
                    String key = new DataInputStream(in).readUTF();
                    System.out.println("Key received: " + key);
                    byte[] valueBytes = tableMap.getEntry(tableName, key);
                    String value = new String(valueBytes);
                    System.out.println("The value to be get is: " + value);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(value);
                    reply = out.toByteArray();
                    break;
                case KVRequestType.SIZE:
                    String tableName2 = new DataInputStream(in).readUTF();
                    int size = tableMap.getSize(tableName2);
                    System.out.println("Size " + size);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size);
                    reply = out.toByteArray();
                    break;
	            case KVRequestType.CHECK:
	                tableName = new DataInputStream(in).readUTF();
	                key = new DataInputStream(in).readUTF();
	                System.out.println("Table Key received: " + key);
	                valueBytes = tableMap.getEntry(tableName, key);
	                boolean entryExists = valueBytes != null;
	                out = new ByteArrayOutputStream();
	                new DataOutputStream(out).writeBoolean(entryExists);
	                reply = out.toByteArray();
	                break;
			    case KVRequestType.TAB_CREATE_CHECK:
			        tableName = new DataInputStream(in).readUTF();
			        System.out.println("Table of Table Key received: " + tableName);
			        Map<String, byte[]> table = tableMap.getName(tableName);
			        boolean tableExists = (table != null);
			        System.out.println("Table exists: " + tableExists);
			        out = new ByteArrayOutputStream();
			        new DataOutputStream(out).writeBoolean(tableExists);
			        reply = out.toByteArray();
			        break;
	        }
	        return reply;
	    } catch (IOException ex) {
	        Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
	        return null;
	    }
    }

}