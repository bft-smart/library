/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.server.defaultservices.blockchain;

import bftsmart.tom.MessageContext;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Map;


/**
 *
 * @author joao
 */
public interface BatchLogger {
    
    default byte[] serializeTransactions(CommandsInfo commandsInfo) throws IOException {
        
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(commandsInfo);
        oos.flush();

        byte[] transBytes = bos.toByteArray();
        oos.close();
        bos.close();
        
        return transBytes;
        
    }
    
    default ByteBuffer prepareTransactions(int cid, byte[] transBytes) {
        
        ByteBuffer buff = ByteBuffer.allocate((Integer.BYTES * 2) + transBytes.length);
        
        buff.putInt(cid);
        buff.putInt(transBytes.length);
        buff.put(transBytes);
        
        buff.flip();
        
        return buff;
    }
    
    default ByteBuffer prepareResults(byte[][] results) {
        
        int resultsSize = 0;
        for (byte[] result : results) {
            resultsSize += result.length;
        }
        
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * (1 + results.length) + (resultsSize));
        
        buff.putInt(results.length);
        
        for (byte[] result : results) {

            buff.putInt(result.length);
            buff.put(result);
        }
        
        buff.flip();
        
        return buff;
    }
    
    default ByteBuffer prepareCertificate(Map<Integer, byte[]> sigs) {
        
        int certSize = 0;
        for (int id : sigs.keySet()) {
            
            certSize += sigs.get(id).length;
        }
        
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * (1 + (sigs.size() * 2)) + (certSize));
        
        buff.putInt(sigs.size());
        
        for (int id : sigs.keySet()) {
        
            buff.putInt(id);
            buff.putInt(sigs.get(id).length);
            buff.put(sigs.get(id));
        }
        
        buff.flip();
        
        return buff;
    }
    
    default ByteBuffer getEOT() {
        
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES);
        buff.putInt(-1);
        
        buff.flip();
        
        return buff;
    }
    
    default ByteBuffer prepareHeader(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] resultsHash,  byte[] prevBlock) {
        
        ByteBuffer buff = ByteBuffer.allocate(Integer.BYTES * 6 + (prevBlock.length + transHash.length + resultsHash.length));
        
        buff.putInt(number);
        buff.putInt(lastCheckpoint);
        buff.putInt(lastReconf);

        buff.putInt(transHash.length);
        buff.put(transHash);
        
        buff.putInt(resultsHash.length);
        buff.put(resultsHash);
        
        buff.putInt(prevBlock.length);
        buff.put(prevBlock);
        
        buff.flip();
        
        return buff;
    }
    
    default byte[] serializeByteBuffers(ByteBuffer[] buffs) {
        
        byte[][] arrays = new byte[buffs.length][];
        
        for (int i = 0; i < buffs.length; i++) {
            
            arrays[i] = buffs[i].array();
        }
        
        return concatenate(arrays);
    }
    
    default byte[] concatenate(byte[][] bytes) {

        int totalLength = 0;
        for (byte[] b : bytes) {
            if (b != null) {
                totalLength += b.length;
            }
        }

        byte[] concat = new byte[totalLength];
        int last = 0;

        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != null) {
                for (int j = 0; j < bytes[i].length; j++) {
                    concat[last + j] = bytes[i][j];
                }

                last += bytes[i].length;
            }

        }

        return concat;
    }
    
    public void storeTransactions(int cid, byte[][] requests, MessageContext[] contexts) throws IOException, InterruptedException;
    
    public void storeResults(byte[][] results) throws IOException, InterruptedException;
    
    public void storeCertificate(Map<Integer, byte[]> sigs) throws IOException, InterruptedException;
    
    public void storeHeader(int number, int lastCheckpoint, int lastReconf,  byte[] transHash,  byte[] resultsHash,  byte[] prevBlock) throws IOException, InterruptedException;
    
    public byte[][] markEndTransactions() throws IOException, InterruptedException;
    
    public void sync() throws IOException, InterruptedException;
    
    public int getLastCachedCID();
    
    public int getFirstCachedCID();
    
    public CommandsInfo[] getCached();
    
    public void clearCached();
    
}
