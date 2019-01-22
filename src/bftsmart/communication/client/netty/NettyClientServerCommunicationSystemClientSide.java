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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.GenericFutureListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.ClosedChannelException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Arrays;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.reconfiguration.ClientViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.TOMUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Paulo
 */
@Sharable
public class NettyClientServerCommunicationSystemClientSide extends SimpleChannelInboundHandler<TOMMessage> implements CommunicationSystemClientSide {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private int clientId;
    protected ReplyReceiver trr;
    //******* EDUARDO BEGIN **************//
    private ClientViewController controller;
    //******* EDUARDO END **************//
    private Map<Integer,NettyClientServerSession> sessionTable = new HashMap<>();
    private ReentrantReadWriteLock rl;
    //the signature engine used in the system
    private Signature signatureEngine;
    //private int signatureLength;
    private boolean closed = false;

    private EventLoopGroup workerGroup;
    
    private SyncListener listener;

    public NettyClientServerCommunicationSystemClientSide(int clientId, ClientViewController controller) {
        super();

        this.clientId = clientId;
        this.workerGroup = new NioEventLoopGroup();
        try {           
            SecretKeyFactory fac = TOMUtil.getSecretFactory();

            this.controller = controller;
            this.listener = new SyncListener();

            //this.st = new Storage(BENCHMARK_PERIOD);
            this.rl = new ReentrantReadWriteLock();

            ChannelFuture future = null;
            int[] currV = controller.getCurrentViewProcesses();
            for (int i = 0; i < currV.length; i++) {
                try {

                    String str = this.clientId + ":" + currV[i];
                    PBEKeySpec spec = TOMUtil.generateKeySpec(str.toCharArray());
                    SecretKey authKey = fac.generateSecret(spec);

                    //EventLoopGroup workerGroup = new NioEventLoopGroup();
                    
                    //try {
                    Bootstrap b = new Bootstrap();
                    b.group(workerGroup);
                    b.channel(NioSocketChannel.class);
                    b.option(ChannelOption.SO_KEEPALIVE, true);
                    b.option(ChannelOption.TCP_NODELAY, true);
                    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000);

                    b.handler(getChannelInitializer());

                    // Start the client.
                    future =  b.connect(controller.getRemoteAddress(currV[i]));					

                    //******* EDUARDO BEGIN **************//

                    //creates MAC stuff
                    Mac macSend = TOMUtil.getMacFactory();
                    macSend.init(authKey);
                    Mac macReceive = TOMUtil.getMacFactory();
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(future.channel(), macSend, macReceive, currV[i]);
                    sessionTable.put(currV[i], cs);

                    logger.info("Connecting to replica " + currV[i] + " at " + controller.getRemoteAddress(currV[i]));
                    //******* EDUARDO END **************//

                    future.awaitUninterruptibly();

                    if (!future.isSuccess()) {
                            logger.error("Impossible to connect to " + currV[i]);
                    }

                } catch (java.lang.NullPointerException ex) {
                        //What is this??? This is not possible!!!
                        logger.debug("Should fix the problem, and I think it has no other implications :-), "
                                        + "but we must make the servers store the view in a different place.");
                } catch (InvalidKeyException ex) {
                        logger.error("Failed to initialize MAC engine",ex);
                } catch (Exception ex){
                        logger.error("Failed to initialize MAC engine",ex);
                }
            }
        } catch (NoSuchAlgorithmException ex) {
                logger.error("Failed to initialize secret key factory",ex);
        }
    }

    @Override
    public void updateConnections() {
        int[] currV = controller.getCurrentViewProcesses();
        try {
            //open connections with new servers
            for (int i = 0; i < currV.length; i++) {
                rl.readLock().lock();
                if (sessionTable.get(currV[i]) == null) {

                    rl.readLock().unlock();
                    rl.writeLock().lock();

                    SecretKeyFactory fac = TOMUtil.getSecretFactory();
                    try {
                        // Configure the client.

                        //EventLoopGroup workerGroup = new NioEventLoopGroup();
                        if( workerGroup == null){
                            workerGroup = new NioEventLoopGroup();
                        }
                        
                        //try {
                        Bootstrap b = new Bootstrap();
                        b.group(workerGroup);
                        b.channel(NioSocketChannel.class);
                        b.option(ChannelOption.SO_KEEPALIVE, true);
                        b.option(ChannelOption.TCP_NODELAY, true);
                        b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000);

                        b.handler(getChannelInitializer());

                        // Start the client.
                        ChannelFuture future =  b.connect(controller.getRemoteAddress(currV[i]));

                        String str = this.clientId + ":" + currV[i];
                        PBEKeySpec spec = TOMUtil.generateKeySpec(str.toCharArray());
                        SecretKey authKey = fac.generateSecret(spec);

                        //creates MAC stuff
                        Mac macSend = TOMUtil.getMacFactory();
                        macSend.init(authKey);
                        Mac macReceive = TOMUtil.getMacFactory();
                        macReceive.init(authKey);
                        NettyClientServerSession cs = new NettyClientServerSession(future.channel(), macSend, macReceive, currV[i]);
                        sessionTable.put(currV[i], cs);

                        logger.info("Connecting to replica " + currV[i] + " at " + controller.getRemoteAddress(currV[i]));
                        //******* EDUARDO END **************//

                        future.awaitUninterruptibly();

                        if (!future.isSuccess()) {
                            logger.error("Impossible to connect to " + currV[i]);
                        }

                    } catch (InvalidKeyException | InvalidKeySpecException ex) {
                        logger.error("Failed to initialize MAC engine",ex);
                    }
                    rl.writeLock().unlock();
                } else {
                    rl.readLock().unlock();
                }
            }
        } catch (NoSuchAlgorithmException ex) {
                logger.error("Failed to initialzie secret key factory",ex);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause)  throws Exception {
        if(cause instanceof ClosedChannelException) {
            logger.error("Connection with replica closed.",cause);
        } else if(cause instanceof ConnectException) {
            logger.error("Impossible to connect to replica.",cause);
        } else {
            logger.error("Replica disconnected.",cause);
        }
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TOMMessage sm) throws Exception {
        
        if(closed){

            closeChannelAndEventLoop(ctx.channel());
            
            return;
        }
        trr.replyReceived(sm);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        
        if(closed){

            closeChannelAndEventLoop(ctx.channel());
            
            return;
        }
        
        logger.info("Channel active");
    }

    public void reconnect(final ChannelHandlerContext ctx){

        rl.writeLock().lock();
    	logger.debug("try to reconnect");

        //Iterator sessions = sessionTable.values().iterator();

        ArrayList<NettyClientServerSession> sessions = new ArrayList<NettyClientServerSession>(sessionTable.values());
        for (NettyClientServerSession ncss : sessions) {
            if (ncss.getChannel() == ctx.channel()) {
                try {
                    // Configure the client.
                    //EventLoopGroup workerGroup = ctx.channel().eventLoop();
                    if( workerGroup == null){
                        workerGroup = new NioEventLoopGroup();
                    }

                    //try {
                    Bootstrap b = new Bootstrap();
                    b.group(workerGroup);
                    b.channel(NioSocketChannel.class);
                    b.option(ChannelOption.SO_KEEPALIVE, true);
                    b.option(ChannelOption.TCP_NODELAY, true);
                    b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,10000);

                    b.handler(getChannelInitializer());

                    if (controller.getRemoteAddress(ncss.getReplicaId()) != null) {

                        ChannelFuture future =  b.connect(controller.getRemoteAddress(ncss.getReplicaId()));

                        //creates MAC stuff
                        Mac macSend = ncss.getMacSend();
                        Mac macReceive = ncss.getMacReceive();
                        NettyClientServerSession cs = new NettyClientServerSession(future.channel(), macSend, macReceive, ncss.getReplicaId());
                        sessionTable.remove(ncss.getReplicaId());
                        sessionTable.put(ncss.getReplicaId(), cs);

                        logger.info("re-connecting to replica "+ncss.getReplicaId()+" at " + controller.getRemoteAddress(ncss.getReplicaId()));
                    } else {
                        // This cleans an olde server from the session table
                        sessionTable.remove(ncss.getReplicaId());
                    }
                } catch (NoSuchAlgorithmException ex) {
                    logger.error("Failed to reconnect to replica",ex);
                }
            }
        }

        //closes all other channels to avoid messages being sent to only a subset of the replicas
        /*Enumeration sessionElements = sessionTable.elements();
        while (sessionElements.hasMoreElements()){
            ((NettyClientServerSession) sessionElements.nextElement()).getChannel().close();
        }*/
        rl.writeLock().unlock();
    }

    @Override
    public void setReplyReceiver(ReplyReceiver trr) {
        this.trr = trr;
    }

    @Override
    public void send(boolean sign, int[] targets, TOMMessage sm) {

        int quorum;
        
        if (controller.getStaticConf().isBFT()) {
                quorum = (int) Math.ceil((controller.getCurrentViewN()
                                + controller.getCurrentViewF()) / 2) + 1;
        } else {
                quorum = (int) Math.ceil((controller.getCurrentViewN()) / 2) + 1;
        }
        
        listener.waitForChannels(quorum); // wait for the previous transmission to complete
        
        logger.debug("Sending request from " + sm.getSender() + " with sequence number " + sm.getSequence() + " to " + Arrays.toString(targets));
                
        if (sm.serializedMessage == null) {

            //serialize message
            DataOutputStream dos = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                dos = new DataOutputStream(baos);
                sm.wExternal(dos);
                dos.flush();
                sm.serializedMessage = baos.toByteArray();
            } catch (IOException ex) {
                logger.debug("Impossible to serialize message: " + sm);
            }
        }

        //Logger.println("Sending message with "+sm.serializedMessage.length+" bytes of content.");

        //produce signature
        if (sign && sm.serializedMessageSignature == null) {
            sm.serializedMessageSignature = signMessage(
                    controller.getStaticConf().getPrivateKey(), sm.serializedMessage);
        }
                
        int sent = 0;
        for (int i = targets.length - 1; i >= 0; i--) {
            
            // This is done to avoid a race condition with the writeAndFush method. Since the method is asynchronous,
            // each iteration of this loop could overwrite the destination of the previous one
            try {
                sm = (TOMMessage) sm.clone();
            } catch (CloneNotSupportedException e) {
                logger.error("Failed to clone TOMMessage",e);
                continue;
            }
            
            sm.destination = targets[i];

            rl.readLock().lock();
            Channel channel = ((NettyClientServerSession) sessionTable.get(targets[i])).getChannel();
            rl.readLock().unlock();
            if (channel.isActive()) {
                sm.signed = sign;
                ChannelFuture f = channel.writeAndFlush(sm);
                                
                f.addListener(listener);
                
                sent++;
            } else {
                logger.debug("Channel to " + targets[i] + " is not connected");
            }
        }

        if (targets.length > controller.getCurrentViewF() && sent < controller.getCurrentViewF() + 1) {
            //if less than f+1 servers are connected send an exception to the client
            throw new RuntimeException("Impossible to connect to servers!");
        }
        if(targets.length == 1 && sent == 0)
                throw new RuntimeException("Server not connected");
    }

    public void sign(TOMMessage sm) {
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
            
            logger.error("Failed to sign TOMMessage", ex);
        }

        //******* EDUARDO BEGIN **************//
        //produce signature
        byte[] data2 = signMessage(controller.getStaticConf().getPrivateKey(), data);
        //******* EDUARDO END **************//

        sm.serializedMessageSignature = data2;
    }

    public byte[] signMessage(PrivateKey key, byte[] message) {
        //long startTime = System.nanoTime();
        try {
            if (signatureEngine == null) {
                    signatureEngine = TOMUtil.getSigEngine();
            }
            byte[] result = null;

            signatureEngine.initSign(key);
            signatureEngine.update(message);
            result = signatureEngine.sign();

            //st.store(System.nanoTime() - startTime);
            return result;
        } catch (Exception e) {
            logger.error("Failed to sign message",e);
            return null;
        }
    }

    @Override
    public void close() {
        this.closed = true;
        //Iterator sessions = sessionTable.values().iterator();
        rl.readLock().lock();
        ArrayList<NettyClientServerSession> sessions = new ArrayList<>(sessionTable.values());
        rl.readLock().unlock();
        for (NettyClientServerSession ncss : sessions) {
            Channel c = ncss.getChannel();           
            closeChannelAndEventLoop(c);
        }
    }

    private ChannelInitializer getChannelInitializer() throws NoSuchAlgorithmException{

        Mac macDummy = TOMUtil.getMacFactory();

        final NettyClientPipelineFactory nettyClientPipelineFactory = new NettyClientPipelineFactory(this, sessionTable, controller, rl);

        ChannelInitializer channelInitializer = new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(nettyClientPipelineFactory.getDecoder());
                ch.pipeline().addLast(nettyClientPipelineFactory.getEncoder());
                ch.pipeline().addLast(nettyClientPipelineFactory.getHandler());

            }
        };					
        return channelInitializer;		
    }

    @Override
    public void channelUnregistered(final ChannelHandlerContext ctx) throws Exception {
        scheduleReconnect(ctx,10);
    }
    
    @Override
    public void channelInactive(final ChannelHandlerContext ctx){
        scheduleReconnect(ctx,10);
    }

    private void closeChannelAndEventLoop(Channel c) {
            // once having an event in your handler (EchoServerHandler)
            // Close the current channel
            c.close();
            // Then close the parent channel (the one attached to the bind)
            if (c.parent() != null) c.parent().close();
            //c.eventLoop().shutdownGracefully();
            workerGroup.shutdownGracefully();
    }
    
    private void scheduleReconnect(final ChannelHandlerContext ctx, int time){
        if(closed){
            closeChannelAndEventLoop(ctx.channel());
            return;
        }
        
        final EventLoop loop = ctx.channel().eventLoop();
        loop.schedule(new Runnable() {
            @Override
            public void run() {
            	reconnect(ctx);
            }
        },time,TimeUnit.SECONDS);
    }
    
    
    private class SyncListener implements GenericFutureListener<ChannelFuture> {
            
            private int remainingFutures;
            
            private final Lock futureLock;
            private final Condition enoughCompleted;
            
            public SyncListener() {
                
                this.remainingFutures = 0;

                this.futureLock = new ReentrantLock();
                this.enoughCompleted = futureLock.newCondition();
            }
            
            @Override
            public void operationComplete(ChannelFuture f) {
                
                this.futureLock.lock();

                this.remainingFutures--;

                if (this.remainingFutures <= 0) {

                    this.enoughCompleted.signalAll();
                }

                logger.debug(this.remainingFutures + " channel operations remaining to complete");
                
                this.futureLock.unlock();
              
            }
            
            public void waitForChannels(int n) {
                
                this.futureLock.lock();
                if (this.remainingFutures > 0) {
                    
                    logger.debug("There are still " + this.remainingFutures + " channel operations pending, waiting to complete");
                    
                    try {
                        this.enoughCompleted.await(1000, TimeUnit.MILLISECONDS); // timeout if a malicous replica refuses to acknowledge the operation as completed
                    } catch (InterruptedException ex) {
                        logger.error("Interruption while waiting on condition", ex);
                    }
                    
                }
                
                    logger.debug("All channel operations completed or timed out");

                this.remainingFutures = n;
                
                this.futureLock.unlock();
            }
            
        }
}
