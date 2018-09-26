/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.communication.client.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import bftsmart.reconfiguration.ViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Paulo Sousa
 */
public class NettyTOMMessageDecoder extends ByteToMessageDecoder {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());


    /**
     * number of measures used to calculate statistics
     */
    //private final int BENCHMARK_PERIOD = 10000;
    private boolean isClient;
    private Map sessionTable;
    //private Storage st;
    private ViewController controller;
    private boolean firstTime;
    private ReentrantReadWriteLock rl;
    //******* EDUARDO BEGIN: commented out some unused variables **************//
    //private long numReceivedMsgs = 0;
    //private long lastMeasurementStart = 0;
    //private long max=0;
    //private Storage st;
    //private int count = 0;
   
    //private Signature signatureEngine;
    
    
     //******* EDUARDO END **************//
    
    private boolean useMAC;
    
    public NettyTOMMessageDecoder(boolean isClient, Map sessionTable, ViewController controller, ReentrantReadWriteLock rl, boolean useMAC) {
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.controller = controller;
        this.firstTime = true;
        this.rl = rl;
        this.useMAC = useMAC;
        logger.debug("new NettyTOMMessageDecoder!!, isClient=" + isClient);
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf buffer, List<Object> list) throws Exception  {

        // Wait until the length prefix is available.
        if (buffer.readableBytes() < Integer.BYTES) {
            return;
        }

        int dataLength = buffer.getInt(buffer.readerIndex());

        //Logger.println("Receiving message with "+dataLength+" bytes.");

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + Integer.BYTES) {
            return;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(Integer.BYTES);

        int size = buffer.readInt();
        byte[] data = new byte[size];
        buffer.readBytes(data);

        byte[] digest = null;
        if (useMAC) {
            
            size = buffer.readInt();
            digest = new byte[size];
            buffer.readBytes(digest);
        }

        byte[] signature = null;
        size = buffer.readInt();            
            
        if (size > 0) {

            signature = new byte[size];
            buffer.readBytes(signature);
        }

        DataInputStream dis = null;
        TOMMessage sm = null;

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            dis = new DataInputStream(bais);
            sm = new TOMMessage();
            sm.rExternal(dis);
            sm.serializedMessage = data;

            if (signature != null) {
                sm.serializedMessageSignature = signature;
                sm.signed = true;
            }
            if (useMAC) {
                sm.serializedMessageMAC = digest;
            }

            if (isClient) {
                //verify MAC
                if (useMAC) {
                    if (!verifyMAC(sm.getSender(), data, digest)) {
                        logger.error("MAC error: message discarded");
                        return;
                    }
                }
            } else { /* it's a server */
                //verifies MAC if it's not the first message received from the client
                rl.readLock().lock();
                if (sessionTable.containsKey(sm.getSender())) {
                    rl.readLock().unlock();
                    if (useMAC) {
                        if (!verifyMAC(sm.getSender(), data, digest)) {
                            logger.debug("MAC error: message discarded");
                            return;
                        }
                    }
                } else {
                    //creates MAC/publick key stuff if it's the first message received from the client
                    logger.debug("Creating MAC/public key stuff, first message from client" + sm.getSender());
                    logger.debug("sessionTable size=" + sessionTable.size());

                    rl.readLock().unlock();
                    
                    SecretKeyFactory fac = TOMUtil.getSecretFactory();
                    String str = sm.getSender() + ":" + this.controller.getStaticConf().getProcessId();                                        
                    PBEKeySpec spec = TOMUtil.generateKeySpec(str.toCharArray());
                    SecretKey authKey = fac.generateSecret(spec);
            
                    Mac macSend = TOMUtil.getMacFactory();
                    macSend.init(authKey);
                    Mac macReceive = TOMUtil.getMacFactory();
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(context.channel(), macSend, macReceive, sm.getSender());
                                       
                    rl.writeLock().lock();
//                    logger.info("PUT INTO SESSIONTABLE - [client id]:"+sm.getSender()+" [channel]: "+cs.getChannel());
                    sessionTable.put(sm.getSender(), cs);
                    logger.debug("active clients " + sessionTable.size());
                    rl.writeLock().unlock();
                    if (useMAC && !verifyMAC(sm.getSender(), data, digest)) {
                        logger.debug("MAC error: message discarded");
                        return;
                    }
                }
            }
            logger.debug("Decoded reply from " + sm.getSender() + " with sequence number " + sm.getSequence());
            list.add(sm);
        } catch (Exception ex) {
            
            logger.error("Failed to decode TOMMessage", ex);
        }
        return;
    }

    boolean verifyMAC(int id, byte[] data, byte[] digest) {
        //long startInstant = System.nanoTime();
        rl.readLock().lock();
        Mac macReceive = ((NettyClientServerSession) sessionTable.get(id)).getMacReceive();
        rl.readLock().unlock();
        boolean result = Arrays.equals(macReceive.doFinal(data), digest);
        //long duration = System.nanoTime() - startInstant;
        //st.store(duration);
        return result;
    }

}
