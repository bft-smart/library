/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package navigators.smart.tom.demo.simplekv;

import java.io.*;
import java.util.TreeMap;
import navigators.smart.tom.MessageContext;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.server.Recoverable;
import navigators.smart.tom.server.SingleExecutable;

/**
 *
 * @author alyssonbessani
 */
public class KVServer implements SingleExecutable, Recoverable {

    private TreeMap<String, String> map = new TreeMap<String, String>();

    @Override
    public byte[] executeOrdered(byte[] command, MessageContext msgCtx) {
        KVMessage request = KVMessage.createFromBytes(command);
        if (request == null) {
            return new KVMessage(KVMessage.Type.valueOf("ERROR"), "", "").getBytes();
        }

        System.out.println("Processing ordered request: " + request);
        switch (request.type) {
            case PUT: {
                map.put(request.key, request.value);
            }break;
            case REMOVE: {
                request.value = map.remove(request.key);
            }break;
            case GET: {
                request.value = map.get(request.key);
            }break;
            case SIZE: {
                request.value = new Integer(map.size()).toString();
            }
        }

        if(request.value == null) {
            request.value = "NOTHING";
        }
        System.out.println("Sending reply: " + request);
        return request.getBytes();
    }

    @Override
    public byte[] executeUnordered(byte[] command, MessageContext msgCtx) {
        KVMessage request = KVMessage.createFromBytes(command);
        if (request == null) {
            return new KVMessage(KVMessage.Type.valueOf("ERROR"), "", "").getBytes();
        }

        System.out.println("Processing unordered request: " + request);
        switch (request.type) {
            case GET: {
                request.value = map.get(request.key);
            }break;
            case SIZE: {
                request.value = new Integer(map.size()).toString();
            }
        }
        
        if(request.value == null) {
            request.value = "NOTHING";
        }
        System.out.println("Sending reply: " + request);
        return request.getBytes();
    }

    @Override
    public byte[] getState() {
        try {
            // serialize to byte array and return
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(map);

            out.flush();
            bos.flush();
            out.close();
            bos.close();
            return bos.toByteArray();
        } catch (IOException ioe) {
            System.err.println("Error serializing state: " + ioe.getMessage());
            return "ERROR".getBytes();
        }
    }

    @Override
    public void setState(byte[] state) {
        try {
            // serialize to byte array and return
            ByteArrayInputStream bis = new ByteArrayInputStream(state);
            ObjectInput in = new ObjectInputStream(bis);
            map = (TreeMap<String, String>) in.readObject();
        } catch (Exception e) {
            System.err.println("Error deserializing state: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        KVServer server = new KVServer();
        new ServiceReplica(new Integer(args[0]), server, server);
    }
}

