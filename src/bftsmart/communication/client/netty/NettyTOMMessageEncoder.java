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
import io.netty.handler.codec.MessageToByteEncoder;

import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;

import bftsmart.tom.core.messages.TOMMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class NettyTOMMessageEncoder extends MessageToByteEncoder<TOMMessage> {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private boolean isClient;
    private Map sessionTable;
    private ReentrantReadWriteLock rl;
    
    private boolean useMAC;

    public NettyTOMMessageEncoder(boolean isClient, Map sessionTable, ReentrantReadWriteLock rl, boolean useMAC){
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.rl = rl;
        this.useMAC = useMAC;
    }

    @Override
	protected void encode(ChannelHandlerContext context, TOMMessage sm, ByteBuf buffer) throws Exception {
        byte[] msgData;
        byte[] macData = null;
        byte[] signatureData = null;

        msgData = sm.serializedMessage;
        if (sm.signed){
            //signature was already produced before            
            signatureData = sm.serializedMessageSignature;
        }
        
        if (useMAC) {
            macData = produceMAC(sm.destination, msgData, sm.getSender());
            if(macData == null) {
            	logger.warn("Uses MAC and the returned MAC is null. Won't write to channel");
            	return;
            }
        }

        int dataLength = Integer.BYTES + msgData.length +
                (useMAC ? macData.length + Integer.BYTES : 0) +
                Integer.BYTES + (signatureData != null ? signatureData.length : 0);
        
        /* msg size */
        buffer.writeInt(dataLength);
        
        /* data to be sent */
        buffer.writeInt(msgData.length);       
        buffer.writeBytes(msgData);
        
         /* MAC */
        if (useMAC) {
            
            buffer.writeInt(macData.length);
            buffer.writeBytes(macData);
            
        }
        /* signature */

        if (signatureData != null) {

                buffer.writeInt(signatureData.length);
                buffer.writeBytes(signatureData);
        } else {
                buffer.writeInt(0);
        }
        
        context.flush();
    }

    byte[] produceMAC(int id, byte[] data, int me) {
        
        rl.readLock().lock();
        NettyClientServerSession session = (NettyClientServerSession)sessionTable.get(id);
        rl.readLock().unlock();
        
        if(session == null) {
        	logger.warn("Session for client " + id + " is null");
        	return null;
        }
        Mac macSend = session.getMacSend();
        return macSend.doFinal(data);
    }

}
