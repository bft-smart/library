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

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import bftsmart.communication.client.CommunicationSystemServerSide;
import bftsmart.communication.client.RequestReceiver;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Level;

/**
 *
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemServerSide extends SimpleChannelInboundHandler<TOMMessage> implements CommunicationSystemServerSide {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private RequestReceiver requestReceiver;
    private HashMap sessionTable;
    private ReentrantReadWriteLock rl;
    private ServerViewController controller;
    private boolean closed = false;
    private Channel mainChannel;

    // This locked seems to introduce a bottleneck and seems useless, but I cannot recall why I added it
    //private ReentrantLock sendLock = new ReentrantLock();
    private NettyServerPipelineFactory serverPipelineFactory;
        
	public NettyClientServerCommunicationSystemServerSide(ServerViewController controller) {
		try {

			this.controller = controller;
			sessionTable = new HashMap();
			rl = new ReentrantReadWriteLock();

			//Configure the server.
			Mac macDummy = TOMUtil.getMacFactory();

			serverPipelineFactory = new NettyServerPipelineFactory(this, sessionTable, controller, rl);

			EventLoopGroup bossGroup = new NioEventLoopGroup();
                        
                        //If the numbers of workers are not specified by the configuration file,
                        //the event group is created with the default number of threads, which
                        //should be twice the number of cores available.
                        int nWorkers = this.controller.getStaticConf().getNumNettyWorkers();
			EventLoopGroup workerGroup = (nWorkers > 0 ? new NioEventLoopGroup(nWorkers) : new NioEventLoopGroup());

			ServerBootstrap b = new ServerBootstrap(); 
			b.group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class) 
			.childHandler(new ChannelInitializer<SocketChannel>() {
				@Override
				public void initChannel(SocketChannel ch) throws Exception {
					ch.pipeline().addLast(serverPipelineFactory.getDecoder());
					ch.pipeline().addLast(serverPipelineFactory.getEncoder());
					ch.pipeline().addLast(serverPipelineFactory.getHandler());
				}
			})	.childOption(ChannelOption.SO_KEEPALIVE, true).childOption(ChannelOption.TCP_NODELAY, true);

                        String myAddress;
                        String confAddress =
                                    controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId()).getAddress().getHostAddress();
                        
                        if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {
                            
                            myAddress = InetAddress.getLoopbackAddress().getHostAddress();
                            
                        }
                        
                        else if (controller.getStaticConf().getBindAddress().equals("")) {
                            
                            myAddress = InetAddress.getLocalHost().getHostAddress();
                              
                            //If Netty binds to the loopback address, clients will not be able to connect to replicas.
                            //To solve that issue, we bind to the address supplied in config/hosts.config instead.
                            if (InetAddress.getLoopbackAddress().getHostAddress().equals(myAddress) && !myAddress.equals(confAddress)) {
                                
                                myAddress = confAddress;
                            }
                            
                            
                        } else {
                            
                            myAddress = controller.getStaticConf().getBindAddress();
                        }
                        
                        int myPort = controller.getStaticConf().getPort(controller.getStaticConf().getProcessId());

			ChannelFuture f = b.bind(new InetSocketAddress(myAddress, myPort)).sync(); 

			logger.info("ID = " + controller.getStaticConf().getProcessId());
			logger.info("N = " + controller.getCurrentViewN());
			logger.info("F = " + controller.getCurrentViewF());
        		logger.info("Port = " + controller.getStaticConf().getPort(controller.getStaticConf().getProcessId()));
			logger.info("requestTimeout = " + controller.getStaticConf().getRequestTimeout());
			logger.info("maxBatch = " + controller.getStaticConf().getMaxBatchSize());
			if (controller.getStaticConf().getUseMACs() == 1) logger.info("Using MACs");
			if(controller.getStaticConf().getUseSignatures() == 1) logger.info("Using Signatures");
                        logger.info("Binded replica to IP address " + myAddress);
                        //******* EDUARDO END **************//
                        
                        mainChannel = f.channel();

		} catch (NoSuchAlgorithmException | InterruptedException | UnknownHostException ex) {
			logger.error("Failed to create Netty communication system",ex);
		}
	}

        private void closeChannelAndEventLoop(Channel c) {
                c.flush();
                c.deregister();
                c.close();
                c.eventLoop().shutdownGracefully();
        }
        
        @Override
        public void shutdown() {
            
            logger.info("Shutting down Netty system");
            
            this.closed = true;

            closeChannelAndEventLoop(mainChannel);
                
            rl.readLock().lock();
            ArrayList<NettyClientServerSession> sessions = new ArrayList<>(sessionTable.values());
            rl.readLock().unlock();
            for (NettyClientServerSession ncss : sessions) {
                
                closeChannelAndEventLoop(ncss.getChannel());

            }
            
            logger.info("NettyClientServerCommunicationSystemServerSide is halting.");

        }
        
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
		
                if (this.closed) {
                    closeChannelAndEventLoop(ctx.channel());
                    return;
                }
            
                if(cause instanceof ClosedChannelException)
			logger.info("Connection with client closed.");
		else {
			logger.error("Impossible to connect to client.",cause);
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, TOMMessage sm) throws Exception {

                if (this.closed) {
                    closeChannelAndEventLoop(ctx.channel());
                    return;
                }
                            
                //delivers message to TOMLayer
		if (requestReceiver == null)
			logger.warn("Request receiver is still null!");
		else requestReceiver.requestReceived(sm);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
            
                if (this.closed) {
                    closeChannelAndEventLoop(ctx.channel());
                    return;
                }
		logger.info("Session Created, active clients=" + sessionTable.size());
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) {
            
                if (this.closed) {
                    closeChannelAndEventLoop(ctx.channel());
                    return;
                }
                
		rl.writeLock().lock();
		try {
			Set s = sessionTable.entrySet();
			Iterator i = s.iterator();
			while (i.hasNext()) {
				Entry m = (Entry) i.next();
				NettyClientServerSession value = (NettyClientServerSession) m.getValue();
				if (ctx.channel().equals(value.getChannel())) {
					int key = (Integer) m.getKey();
					logger.info("Removing client channel with ID= " + key);
					sessionTable.remove(key);
					logger.info("Active clients=" + sessionTable.size());
					break;
				}
			}
			
            

		} finally {
			rl.writeLock().unlock();
		}
		logger.debug("Session Closed, active clients=" + sessionTable.size());
	}

	@Override
	public void setRequestReceiver(RequestReceiver tl) {
		this.requestReceiver = tl;
	}

	@Override
	public void send(int[] targets, TOMMessage sm, boolean serializeClassHeaders) {

		//serialize message
		DataOutputStream dos = null;

		byte[] data = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			dos = new DataOutputStream(baos);
			sm.wExternal(dos);
			dos.flush();
			data = baos.toByteArray();
			sm.serializedMessage = data;
		} catch (IOException ex) {
			logger.error("Failed to serialize message.", ex);
		}

		//replies are not signed in the current JBP version
		sm.signed = false;
		//produce signature if necessary (never in the current version)
		if (sm.signed) {
			//******* EDUARDO BEGIN **************//
			byte[] data2 = TOMUtil.signMessage(controller.getStaticConf().getPrivateKey(), data);
			//******* EDUARDO END **************//
			sm.serializedMessageSignature = data2;
		}

		for (int i = 0; i < targets.length; i++) {
                    
                        // This is done to avoid a race condition with the writeAndFush method. Since the method is asynchronous,
                        // each iteration of this loop could overwrite the destination of the previous one
                        try {
                            sm = (TOMMessage) sm.clone();
                        } catch (CloneNotSupportedException ex) {
                            logger.error("Failed to clone TOMMessage",ex);
                            continue;
                        }
                    
			rl.readLock().lock();
			//sendLock.lock();
			try {       
				NettyClientServerSession ncss = (NettyClientServerSession) sessionTable.get(targets[i]);
				if (ncss != null) {
					Channel session = ncss.getChannel();
					sm.destination = targets[i];
					//send message
					session.writeAndFlush(sm); // This used to invoke "await". Removed to avoid blockage and race condition.
                                
                                ///////TODO: replace this patch for a proper client preamble
                                } else if (sm.getSequence() >= 0 && sm.getSequence() <= 5) {
                                    
                                        final int id = targets[i];
                                        final TOMMessage msg = sm;
                                        
                                        Thread t = new Thread() {
                                                                                        
                                            public void run() {
                                                
                                                logger.warn("Received request from " + id + " before establishing Netty connection. Re-trying until connection is established");

                                                NettyClientServerSession ncss = null;
                                                while (ncss == null) {

                                                    rl.readLock().lock();
                                                    
                                                    try {
                                                        Thread.sleep(1000);
                                                    } catch (InterruptedException ex) {
                                                        logger.error("Interruption while sleeping", ex);
                                                    }
                                                    
                                                    ncss = (NettyClientServerSession) sessionTable.get(id);
                                                    if (ncss != null) {
                                                            Channel session = ncss.getChannel();
                                                            msg.destination = id;
                                                            //send message
                                                            session.writeAndFlush(msg);
                                                    }

                                                    rl.readLock().unlock();
                                                    
                                                }
                                                 
                                                logger.info("Connection with " + id + " established!");

                                                
                                            }
                                            
                                        };
                                        
                                        t.start();
                                        ///////////////////////////////////////////
				} else {
                                    logger.warn("!!!!!!!!NettyClientServerSession is NULL !!!!!! sequence: " + sm.getSequence() + ", ID; " + targets[i]);
                                }
			} finally {
				//sendLock.unlock();
				rl.readLock().unlock();
			}
		}
	}

    @Override
    public int[] getClients() {
        
        rl.readLock().lock();
        Set s = sessionTable.keySet();
        int[] clients = new int[s.size()];
        Iterator it = s.iterator();
        int i = 0;
        while (it.hasNext()) {
            
            clients[i] = ((Integer) it.next()).intValue();
            i++;
        }
        
        rl.readLock().unlock();
        
        return clients;
    }

}
