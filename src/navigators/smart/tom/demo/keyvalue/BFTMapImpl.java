/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.demo.keyvalue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ServiceReplica;

/**
 *
 * @author sweta
 */
//This class extends ServiceReplica and overrides its functions serializeState(), deserializeState() and executeOrdered().
public class BFTMapImpl extends ServiceReplica {

    BFTMAPUtil bftReplica = new BFTMAPUtil();
    
    //The constructor passes the id of the server to the super class
    public BFTMapImpl(int id) {
        super(id);
    }

    @Override
    protected byte[] serializeState() {
        try {

            //save to file (not needed for now)
            ObjectOutput out = new ObjectOutputStream(new FileOutputStream("MyObject.ser"));
            out.writeObject(bftReplica);
            out.close();

            // serialize to byte array and return
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(bos);
            out.writeObject(bftReplica);
            out.close();
            return bos.toByteArray();
        } catch (IOException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
            return new byte[0];
        }
    }

    @Override
    protected void deserializeState(byte[] state) {
        try {
            //save to file (not needed for now)
            ObjectInput in = new ObjectInputStream(new FileInputStream("MyObject.ser"));
            bftReplica = (BFTMAPUtil) in.readObject();
            in.close();

            // serialize to byte array and return
            ByteArrayInputStream bis = new ByteArrayInputStream(state);
            in = new ObjectInputStream(bis);
            bftReplica = (BFTMAPUtil) in.readObject();
            in.close();

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
                case BFTMAPUtil.PUT:
                    String tableName = new DataInputStream(in).readUTF();
                    String key = new DataInputStream(in).readUTF();
                    String value = new DataInputStream(in).readUTF();
                    byte[] b = value.getBytes();
                    System.out.println("Key received" + key);
                    byte[] ret = bftReplica.addData(tableName, key, b);

                    if (ret == null) {
                        System.out.println("inside if");
                        ret = new byte[0];
                    }
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(new String(ret));
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.GET:
                    String tableName1 = new DataInputStream(in).readUTF();
                    System.out.println("tablename" + tableName1);
                    String id1 = new DataInputStream(in).readUTF();
                    System.out.println("ID received" + id1);
                    byte[] b1 = bftReplica.getEntry(tableName1, id1);
                    String value1 = new String(b1);
                    System.out.println("The value to be get is" + value1);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(value1);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.SIZE:
                    String tableName2 = new DataInputStream(in).readUTF();
                    int size = bftReplica.getSize(tableName2);
                    System.out.println("Size " + size);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.SIZE_TABLE:
                    int size1 = bftReplica.getSizeofTable();
                    System.out.println("Size " + size1);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeInt(size1);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.REMOVE:
                    String tableName4 = new DataInputStream(in).readUTF();
                    String id2 = new DataInputStream(in).readUTF();
                    System.out.println("ID received" + id2);
                    byte[] b2 = bftReplica.removeEntry(tableName4, id2);
                    String value2 = new String(b2);
                    System.out.println("Value removed is : " + value2);
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBytes(value2);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.TAB_CREATE_CHECK:
                    String id3 = new DataInputStream(in).readUTF();
                    System.out.println("Table of Table Key received" + id3);
                    HashMap<String, byte[]> b3 = bftReplica.getName(id3);
                    boolean tableExists = b3 != null;
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBoolean(tableExists);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.CHECK:

                    String tableName7 = new DataInputStream(in).readUTF();
                    String id4 = new DataInputStream(in).readUTF();
                    System.out.println("Table Key received" + id4);
                    byte[] b5 = bftReplica.getEntry(tableName7, id4);
                    boolean tableExists1 = b5 != null;
                    out = new ByteArrayOutputStream();
                    new DataOutputStream(out).writeBoolean(tableExists1);
                    reply = out.toByteArray();
                    break;
                case BFTMAPUtil.TAB_CREATE:
                    String table4 = new DataInputStream(in).readUTF();
                    //ByteArrayInputStream in1 = new ByteArrayInputStream(command);
                    ObjectInputStream in2 = new ObjectInputStream(in);
                    HashMap<String, byte[]> table = null;
                    try {
                        table = (HashMap<String, byte[]>) in2.readObject();
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    HashMap<String, byte[]> table1 = bftReplica.addTable(table4, table);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    ObjectOutputStream out1 = new ObjectOutputStream(bos);
                    out1.writeObject(table1);
                    out1.close();
                    in.close();
                    reply = bos.toByteArray();
                    break;

                case BFTMAPUtil.TAB_REMOVE:
                    String tableName3 = new DataInputStream(in).readUTF();
                    HashMap<String, byte[]> info4 = bftReplica.removeTable(tableName3);
                    ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
                    ObjectOutputStream out3 = new ObjectOutputStream(bos2);
                    out3.writeObject(info4);
                    out3.close();
                    out3.close();
                    reply = bos2.toByteArray();
                    break;


            }
            return reply;
        } catch (IOException ex) {
            Logger.getLogger(BFTMapImpl.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    @Override
    @SuppressWarnings("static-access")
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        System.err.println("WARNING: unordered operations not supported!");
        return new byte[0];
    }

}