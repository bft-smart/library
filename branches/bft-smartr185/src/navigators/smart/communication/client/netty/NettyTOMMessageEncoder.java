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

import static org.jboss.netty.buffer.ChannelBuffers.buffer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;

import navigators.smart.tom.core.messages.TOMMessage;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;


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
