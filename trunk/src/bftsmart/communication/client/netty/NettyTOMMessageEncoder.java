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

import static org.jboss.netty.buffer.ChannelBuffers.buffer;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;


import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import bftsmart.tom.core.messages.TOMMessage;


@ChannelPipelineCoverage("all")
public class NettyTOMMessageEncoder extends SimpleChannelHandler {
    
    private boolean isClient;
    private Map sessionTable;
    private int macLength;
    private int signatureLength;
    private ReentrantReadWriteLock rl;
    private boolean useMAC;

    public NettyTOMMessageEncoder(boolean isClient, Map sessionTable, int macLength, ReentrantReadWriteLock rl, int signatureLength, boolean useMAC){
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.macLength = macLength;
        this.rl = rl;
        this.signatureLength = signatureLength;
        this.useMAC = useMAC;
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        TOMMessage sm = (TOMMessage) e.getMessage();
        byte[] msgData;
        byte[] macData = null;
        byte[] signatureData = null;

        msgData = sm.serializedMessage;
        if (sm.signed){
            //signature was already produced before            
            signatureData = sm.serializedMessageSignature;
            if (signatureData.length != signatureLength)
                System.out.println("WARNING: message signature has size "+signatureData.length+" and should have "+signatureLength);
        }
        
        if (useMAC)
            macData = produceMAC(sm.destination,msgData);

        int dataLength = 1+msgData.length+(macData==null?0:macData.length)+
                (signatureData==null?0:signatureData.length);

        //Logger.println("Sending message with "+dataLength+" bytes.");

        ChannelBuffer buf = buffer(4+dataLength);
        /* msg size */
        buf.writeInt(dataLength);
        /* control byte indicating if the message is signed or not */
        buf.writeByte(sm.signed==true?(byte)1:(byte)0);       
        /* data to be sent */
        buf.writeBytes(msgData);
         /* MAC */
        if (useMAC)
            buf.writeBytes(macData);
        /* signature */
        if (signatureData != null)
            buf.writeBytes(signatureData);

        Channels.write(ctx, e.getFuture(), buf);        
    }

    byte[] produceMAC(int id, byte[] data){
        rl.readLock().lock();
        Mac macSend = ((NettyClientServerSession)sessionTable.get(id)).getMacSend();
        rl.readLock().unlock();
        return macSend.doFinal(data);
    }

}
