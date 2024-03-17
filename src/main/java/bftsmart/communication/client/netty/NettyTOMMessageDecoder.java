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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.reconfiguration.ViewController;
import bftsmart.tom.core.messages.TOMMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

/**
 *
 * @author Paulo Sousa
 */
public class NettyTOMMessageDecoder extends ByteToMessageDecoder {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final boolean isClient;
    private final ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable;
    private final ViewController controller;
	private final ReentrantReadWriteLock rl;
    private int bytesToSkip;
    
    public NettyTOMMessageDecoder(boolean isClient, 
    		ConcurrentHashMap<Integer, NettyClientServerSession> sessionTable, 
    		ViewController controller, 
    		ReentrantReadWriteLock rl) {
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.controller = controller;
		boolean firstTime = true;
        this.rl = rl;
        this.bytesToSkip = 0;
        logger.debug("new NettyTOMMessageDecoder!!, isClient=" + isClient);
        logger.trace("\n\t isClient: {};"
        		+ 	 "\n\t sessionTable: {};"
        		+ 	 "\n\t controller: {};"
        		+ 	 "\n\t firstTime: {};"
        		+ 	 "\n\t rl: {};"
        		+ 	 "\n\t signatureSize: {};", 
        		new Object[] {isClient, 
        					  sessionTable.toString(),
        					  controller,
						firstTime,
        					  rl});
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf buffer, List<Object> list) throws Exception  {
        // Skip bytes if necessary.
        if (bytesToSkip != 0) {
            int readable = buffer.readableBytes();
            if(readable > bytesToSkip) {
                buffer.skipBytes(bytesToSkip);
                bytesToSkip = 0;
            } else {
                buffer.skipBytes(readable);
                bytesToSkip -= readable;
                return;
            }
        }

        int dataLength;
        do {
            // Wait until the length prefix is available.
            if (buffer.readableBytes() < Integer.BYTES) {
                return;
            }

            dataLength = buffer.getInt(buffer.readerIndex());

            //Logger.println("Receiving message with "+dataLength+" bytes.");

            // Skip the request if it is too large
            if (dataLength > controller.getStaticConf().getMaxRequestSize() && !isClient) {
                logger.warn("Discarding request with " + dataLength + " bytes");
                buffer.skipBytes(Integer.BYTES);
                int readableBytes = buffer.readableBytes();
                if (dataLength >= readableBytes) {
                    buffer.skipBytes(readableBytes);
                    bytesToSkip = dataLength - readableBytes;
                    return;
                } else {
                    buffer.skipBytes(dataLength);
                    // Now read dataLength again.
                }
            } else {
                break;
            }
        } while (true);

        // Wait until the whole data is available.
        if (buffer.readableBytes() < dataLength + Integer.BYTES) {
            return;
        }

        // Skip the length field because we know it already.
        buffer.skipBytes(Integer.BYTES);

        int size = buffer.readInt();
        byte[] data = new byte[size];
        buffer.readBytes(data);

        byte[] signature = null;
        size = buffer.readInt();            
            
        if (size > 0) {
            signature = new byte[size];
            buffer.readBytes(signature);
        }

		size = buffer.readInt();
		byte[] replicaSpecificContent = null;
		if (size > -1) {
			replicaSpecificContent = new byte[size];
			buffer.readBytes(replicaSpecificContent);
		}


		try {
			TOMMessage sm = new TOMMessage();
            sm.deserialize(data);
            sm.serializedMessage = data;

            if (signature != null) {
                sm.serializedMessageSignature = signature;
                sm.signed = true;
            }

			sm.setReplicaSpecificContent(replicaSpecificContent);

            if (!isClient) {                
                rl.readLock().lock();                
                if (!sessionTable.containsKey(sm.getSender())) {
                    rl.readLock().unlock();
              
                    NettyClientServerSession cs = new NettyClientServerSession(
                    		context.channel(), 
                    		sm.getSender());
                                       
                    rl.writeLock().lock();
                    sessionTable.put(sm.getSender(), cs);
                    logger.debug("Active clients: {}", sessionTable.size());
                    rl.writeLock().unlock();
                    
                }else {
                	rl.readLock().unlock();   
                }
            }
            logger.debug("Decoded reply from {} with sequence number {}", sm.getSender(), sm.getSequence());
            list.add(sm);
        } catch (Exception ex) {
            
            logger.error("Failed to decode TOMMessage", ex);
        }
	}

}
