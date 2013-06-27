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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;



import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.handler.codec.frame.FrameDecoder;

import bftsmart.reconfiguration.ViewManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Logger;

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
    private Map sessionTable;
    private SecretKey authKey;
    //private Storage st;
    private int macSize;
    private int signatureSize;
    private ViewManager manager;
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

    public NettyTOMMessageDecoder(boolean isClient, Map sessionTable, SecretKey authKey, int macLength, ViewManager manager, ReentrantReadWriteLock rl, int signatureLength, boolean useMAC) {
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.authKey = authKey;
        //this.st = new Storage(benchmarkPeriod);
        this.macSize = macLength;
        this.manager = manager;
        this.firstTime = true;
        this.rl = rl;
        this.signatureSize = signatureLength;
        //this.st = new Storage(BENCHMARK_PERIOD);
        this.useMAC = useMAC;
        bftsmart.tom.util.Logger.println("new NettyTOMMessageDecoder!!, isClient=" + isClient);
    }

    @Override
    protected Object decode(
            ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer) {

        // Wait until the length prefix is available.
        if (buffer.readableBytes() < 4) {
            return null;
        }

        int dataLength = buffer.getInt(buffer.readerIndex());

        //Logger.println("Receiving message with "+dataLength+" bytes.");

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + 4) {
            return null;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(4);

        int totalLength = dataLength - 1;

        //read control byte indicating if message is signed
        byte signed = buffer.readByte();

        int authLength = 0;

        if (signed == 1) {
            authLength += signatureSize;
        }
        if (useMAC) {
            authLength += macSize;
        }

        byte[] data = new byte[totalLength - authLength];
        buffer.readBytes(data);

        byte[] digest = null;
        if (useMAC) {
            digest = new byte[macSize];
            buffer.readBytes(digest);
        }

        byte[] signature = null;
        if (signed == 1) {
            signature = new byte[signatureSize];
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

            if (signed == 1) {
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
                        Logger.println("MAC error: message discarded");
                        return null;
                    }
                }
            } else { /* it's a server */
                //verifies MAC if it's not the first message received from the client
                rl.readLock().lock();
                if (sessionTable.containsKey(sm.getSender())) {
                    rl.readLock().unlock();
                    if (useMAC) {
                        if (!verifyMAC(sm.getSender(), data, digest)) {
                            Logger.println("MAC error: message discarded");
                            return null;
                        }
                    }
                } else {
                    //creates MAC/publick key stuff if it's the first message received from the client
                    bftsmart.tom.util.Logger.println("Creating MAC/public key stuff, first message from client" + sm.getSender());
                    bftsmart.tom.util.Logger.println("sessionTable size=" + sessionTable.size());

                    rl.readLock().unlock();
                    
                    //******* EDUARDO BEGIN **************//
                    Mac macSend = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                    macSend.init(authKey);
                    Mac macReceive = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(channel, macSend, macReceive, sm.getSender(), manager.getStaticConf().getRSAPublicKey(), new ReentrantLock());
                    //******* EDUARDO END **************//

                    rl.writeLock().lock();
                    sessionTable.put(sm.getSender(), cs);
                    bftsmart.tom.util.Logger.println("#active clients " + sessionTable.size());
                    rl.writeLock().unlock();
                    if (useMAC && !verifyMAC(sm.getSender(), data, digest)) {
                        Logger.println("MAC error: message discarded");
                        return null;
                    }
                }
            }

            return sm;
        } catch (Exception ex) {
            bftsmart.tom.util.Logger.println("Impossible to decode message: "+
                    ex.getMessage());
            ex.printStackTrace();
        }
        return null;
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
