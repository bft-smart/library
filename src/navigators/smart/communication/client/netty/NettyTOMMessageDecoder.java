/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.communication.client.netty;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Configuration;
import navigators.smart.tom.util.TOMConfiguration;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.frame.FrameDecoder;


/**
 *
 * @author Paulo Sousa
 */

@ChannelPipelineCoverage("one")
public class NettyTOMMessageDecoder extends FrameDecoder {

    /**
     * number of measures used to calculate statistics
     */
    //private final int BENCHMARK_PERIOD = 10000;

    private boolean isClient;
    private Hashtable<Integer, NettyClientServerSession> sessionTable;
    private SecretKey authKey;
    //private Storage st;
    private int macSize;
    private int signatureSize;
    private Configuration conf;
//    private boolean firstTime;
    private ReentrantReadWriteLock rl;
//    private long numReceivedMsgs = 0;
//    private long lastMeasurementStart = 0;
//    private long max=0;
    private Signature signatureEngine;
    //private Storage st;
//    private int count = 0;
    private boolean useMAC;
    
    private boolean connected;
    
    public NettyTOMMessageDecoder(boolean isClient, Hashtable<Integer,NettyClientServerSession> sessionTable, SecretKey authKey, int macLength, Configuration conf, ReentrantReadWriteLock rl, int signatureLength, boolean useMAC){
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.authKey = authKey;
        //this.st = new Storage(benchmarkPeriod);
        this.macSize = macLength;
        this.conf = conf;
//        this.firstTime = true;
        this. rl = rl;
        this.signatureSize = signatureLength;
        //this.st = new Storage(BENCHMARK_PERIOD);
        this.useMAC = useMAC;
        connected= isClient; //servers wait for connections, clients establish them and are connected by default
        navigators.smart.tom.util.Logger.println("new NettyTOMMessageDecoder!!, isClient="+isClient);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) {

        // Wait until the length prefix is available.
        if (buffer.readableBytes() < 4) {
            return null;
        }

        int dataLength = buffer.getInt(buffer.readerIndex());
        //establish connection
        if(!connected){
        	// we got id of the client, skip bytes, init connection and finish
        	buffer.skipBytes(4);
        	initConnection(dataLength, channel);
        	connected = true;
        	return null;
        }

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + 4) {
            return null;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(4);

        //calculate lenght without signed indication bit
        int totalLength = dataLength-1;

        //read control byte indicating if message serialization includes class header
//        byte hasClassHeader = buffer.readByte();

        //read control byte indicating if message is signed
        byte signed = buffer.readByte();

        int authLength = 0;
 
        if (signed==1)
            authLength += signatureSize;
        if (useMAC)
            authLength += macSize;
        
        byte[] data = new byte[totalLength-authLength];
        buffer.readBytes(data);        
        
        byte[] digest = null;
        if (useMAC){
            digest = new byte[macSize];
            buffer.readBytes(digest);
        }
        
        byte[] signature = null;
        if (signed==1){
            signature = new byte[signatureSize];
            buffer.readBytes(signature);
        }

//        DataInputStream dis = null;
        DataInputStream dis = null;
        TOMMessage sm = null;
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
                //if class headers were serialized
                dis = new DataInputStream(bais);
                sm = new TOMMessage(dis);
            //TOMMessage sm = (TOMMessage) ois.readObject();
            sm.serializedMessage = data;
            if (signed==1){
                sm.serializedMessageSignature = signature;
                sm.signed = true;
            }
            if (useMAC)
                sm.serializedMessageMAC = digest;
            
            if (isClient){
                //verify MAC
                if (useMAC) {
                    if (!verifyMAC(sm.getSender(), data, digest)){
                        Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "MAC error: message discarded");
                        return null;
                    }
                }
            }
            else { /* it's a server */
                //verifies MAC if it's not the first message received from the client
                rl.readLock().lock();
                if (sessionTable.containsKey(sm.getSender())){
                    rl.readLock().unlock();
                    if (useMAC){
                        if (!verifyMAC(sm.getSender(), data, digest)){
                            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "MAC error: message discarded");
                            return null;
                        }
                    }
                    if (signed==1) {
                        /* this is now done by the TOMLayer
                        if (!verifySignature(sm.getSender(), data, digest)){
                            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "Signature error: message from "+sm.getSender()+" discarded");
                            System.out.println("(signature error) Message: "+TOMUtil.byteArrayToString(data));
                            System.out.println("(signature error) signature received: "+TOMUtil.byteArrayToString(digest));
                            return null;
                        }
                         */
                    }
/*
                    numReceivedMsgs++;                    
                    if (numReceivedMsgs == 1) {
                        lastMeasurementStart = System.currentTimeMillis();
                    } else if (numReceivedMsgs==BENCHMARK_PERIOD) {
                        long elapsedTime = System.currentTimeMillis() - lastMeasurementStart;
                        double opsPerSec_ = ((double)BENCHMARK_PERIOD)/(elapsedTime/1000.0);
                        long opsPerSec = Math.round(opsPerSec_);
                        if (opsPerSec>max)
                            max = opsPerSec;                      
                        br.ufsc.das.tom.util.Logger.println("(Netty decoder) (from: "+sm.getSender()+") Last "+BENCHMARK_PERIOD+" messages were received at a rate of " + opsPerSec + " msgs per second");
                        br.ufsc.das.tom.util.Logger.println("(Netty decoder) (from: "+sm.getSender()+") Maximum throughput until now: " + max + " msgs per second");
                        numReceivedMsgs = 0;
                    }
 */
                }
                else {
                    rl.readLock().unlock();
//                    initConnection(sm.getSender(),channel);
//                    if (useMAC){
//                        if (!verifyMAC(sm.getSender(), data, digest)){
//                            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "MAC error: message discarded");
//                            return null;
//                        }
//                    }
                    Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.WARNING, "Got message from unestablished connection");
                   
                }
            }
/*
            if (st.getCount()==1000){
                    System.out.println("MAC: Average time for "+benchmarkPeriod+" executions (-10%) = "+this.st.getAverage(true)/1000+ " us ");
                    System.out.println("MAC: Standard desviation for "+benchmarkPeriod+" executions (-10%) = "+this.st.getDP(true)/1000 + " us ");
                    System.out.println("MAC: Average time for "+benchmarkPeriod+" executions (all samples) = "+this.st.getAverage(false)/1000+ " us ");
                    System.out.println("MAC: Standard desviation for "+benchmarkPeriod+" executions (all samples) = "+this.st.getDP(false)/1000 + " us ");
                    System.out.println("MAC: Maximum time for "+benchmarkPeriod+" executions (-10%) = "+this.st.getMax(true)/1000+ " us ");
                    System.out.println("MAC: Maximum time for "+benchmarkPeriod+" executions (all samples) = "+this.st.getMax(false)/1000+ " us ");
                    st = new Storage(benchmarkPeriod);
            }
*/

        //if I'm a replica then obtain and lock the clientLock in order to guarantee at most one thread per client
            /*
        if (!isClient) {
            rl.readLock().lock();
            Lock clientLock = ((NettyClientServerSession)sessionTable.get(sm.getSender())).getLock();
            rl.readLock().unlock();
            clientLock.lock();
        }
             */
           
        return sm;
    }
        catch (IOException ex) {
            Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
     
     public void initConnection(int sender, Channel channel ) {
		try {
			// creates MAC/publick key stuff if it's the first message received
			// from the client
			navigators.smart.tom.util.Logger.println("Creating MAC/public key stuff, first message from client" + sender);
			navigators.smart.tom.util.Logger.println("sessionTable size=" + sessionTable.size());
			Mac macSend = Mac.getInstance(conf.getHmacAlgorithm());
			macSend.init(authKey);
			Mac macReceive = Mac.getInstance(conf.getHmacAlgorithm());
			macReceive.init(authKey);
			NettyClientServerSession cs = new NettyClientServerSession(channel, macSend, macReceive, sender, TOMConfiguration
					.getRSAPublicKey(sender), new ReentrantLock());
			rl.writeLock().lock();
			sessionTable.put(sender, cs);
			System.out.println("#active clients " + sessionTable.size());
			rl.writeLock().unlock();
		} catch (InvalidKeyException ex) {
			Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
		} catch (NoSuchAlgorithmException ex) {
			Logger.getLogger(NettyTOMMessageDecoder.class.getName()).log(Level.SEVERE, null, ex);
		}    
	}

	boolean verifyMAC(int id, byte[] data, byte[] digest){
        //long startInstant = System.nanoTime();
        rl.readLock().lock();
        Mac macReceive = ((NettyClientServerSession)sessionTable.get(id)).getMacReceive();
        rl.readLock().unlock();
        boolean result = Arrays.equals(macReceive.doFinal(data), digest);
        //long duration = System.nanoTime() - startInstant;
        //st.store(duration);
        return result;
    }

    boolean verifySignature(int id, byte[] data, byte[] digest){
         //long startInstant = System.nanoTime();
        rl.readLock().lock();
        PublicKey pk = ((NettyClientServerSession)sessionTable.get(id)).getPublicKey();
        rl.readLock().unlock();
        //long duration = System.nanoTime() - startInstant;
        //st.store(duration);
        return verifySignatureAux(pk, data, digest);
    }


   /**
     * Verify the signature of a message.
     *
     * @param key the public key to be used to verify the signature
     * @param message the signed message
     * @param signature the signature to be verified
     * @return the signature
     */
    public boolean verifySignatureAux(PublicKey key, byte[] message, byte[] signature) {
//        long startTime = System.nanoTime();
        try {
            if (signatureEngine == null) {
                signatureEngine = Signature.getInstance("SHA1withRSA");                
            }

            signatureEngine.initVerify(key);

            signatureEngine.update(message);

            boolean result = signatureEngine.verify(signature);
            /*
            st.store(System.nanoTime()-startTime);
            //statistics about signature execution time
            count++;
            if (count%BENCHMARK_PERIOD==0){
                System.out.println("-- (NettyDecoder) Signature verification benchmark:--");
                System.out.println("Average time for " + BENCHMARK_PERIOD + " signature verifications (-10%) = " + st.getAverage(true) / 1000 + " us ");
                System.out.println("Standard desviation for " + BENCHMARK_PERIOD + " signature verifications (-10%) = " + st.getDP(true) / 1000 + " us ");
                System.out.println("Average time for " + BENCHMARK_PERIOD + " signature verifications (all samples) = " + st.getAverage(false) / 1000 + " us ");
                System.out.println("Standard desviation for " + BENCHMARK_PERIOD + " signature verifications (all samples) = " + st.getDP(false) / 1000 + " us ");
                System.out.println("Maximum time for " + BENCHMARK_PERIOD + " signature verifications (-10%) = " + st.getMax(true) / 1000 + " us ");
                System.out.println("Maximum time for " + BENCHMARK_PERIOD + " signature verifications (all samples) = " + st.getMax(false) / 1000 + " us ");

                count = 0;
                st.reset();
            }
             */
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
