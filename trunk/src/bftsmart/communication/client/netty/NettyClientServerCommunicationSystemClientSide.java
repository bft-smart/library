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
package bftsmart.communication.client.netty;

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
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;


import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.reconfiguration.ClientViewManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;

/**
 *
 * @author Paulo
 */
@ChannelPipelineCoverage("all")
public class NettyClientServerCommunicationSystemClientSide extends SimpleChannelUpstreamHandler implements CommunicationSystemClientSide {

    //private static final int MAGIC = 59;
    //private static final int CONNECT_TIMEOUT = 3000;
    private static final String PASSWORD = "newcs";
    //private static final int BENCHMARK_PERIOD = 10000;
    protected ReplyReceiver trr;
    //******* EDUARDO BEGIN **************//
    private ClientViewManager manager;
    //******* EDUARDO END **************//
    private Map sessionTable = new HashMap();
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    //the signature engine used in the system
    private Signature signatureEngine;
    //private Storage st;
    //private int count = 0;
    private int signatureLength;
    private boolean closed = false;

    public NettyClientServerCommunicationSystemClientSide(ClientViewManager manager) {
        super();
        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);

            this.manager = manager;
            //this.st = new Storage(BENCHMARK_PERIOD);
            this.rl = new ReentrantReadWriteLock();
            Mac macDummy = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
            signatureLength = TOMUtil.getSignatureSize(manager);


            int[] currV = manager.getCurrentViewProcesses();
            for (int i = 0; i < currV.length; i++) {
                try {
                    // Configure the client.
                    ClientBootstrap bootstrap = new ClientBootstrap(
                            new NioClientSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

                    bootstrap.setOption("tcpNoDelay", true);
                    bootstrap.setOption("keepAlive", true);
                    bootstrap.setOption("connectTimeoutMillis", 10000);

                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable,
                            authKey, macDummy.getMacLength(), manager, rl, signatureLength, new ReentrantLock()));

                    //******* EDUARDO BEGIN **************//

                    // Start the connection attempt.
                    ChannelFuture future = bootstrap.connect(manager.getRemoteAddress(currV[i]));

                    //creates MAC stuff
                    Mac macSend = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                    macSend.init(authKey);
                    Mac macReceive = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend,
                            macReceive, currV[i], manager.getStaticConf().getRSAPublicKey(currV[i]), new ReentrantLock());
                    sessionTable.put(currV[i], cs);

                    System.out.println("Connecting to replica " + currV[i] + " at " + manager.getRemoteAddress(currV[i]));
                    //******* EDUARDO END **************//

                    future.awaitUninterruptibly();

                    if (!future.isSuccess()) {
                        System.err.println("Impossible to connect to " + currV[i]);
                    }

                } catch (java.lang.NullPointerException ex) {
                    //What the fuck is this??? This is not possible!!!
                    System.err.println("Should fix the problem, and I think it has no other implications :-), "
                            + "but we must make the servers store the view in a different place.");

                } catch (InvalidKeyException ex) {
                    ex.printStackTrace(System.err);
                }
            }
        } catch (InvalidKeySpecException ex) {
            ex.printStackTrace(System.err);
        } catch (NoSuchAlgorithmException ex) {
            ex.printStackTrace(System.err);
        }
    }

    @Override
    public void updateConnections() {
        int[] currV = manager.getCurrentViewProcesses();
        try {
            Mac macDummy = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
            
            //open connections with new servers
            for (int i = 0; i < currV.length; i++) {
                rl.readLock().lock();
                if (sessionTable.get(currV[i]) == null) {

                    rl.readLock().unlock();
                    rl.writeLock().lock();
                    try {
                        // Configure the client.
                        ClientBootstrap bootstrap = new ClientBootstrap(
                                new NioClientSocketChannelFactory(
                                Executors.newCachedThreadPool(),
                                Executors.newCachedThreadPool()));

                        bootstrap.setOption("tcpNoDelay", true);
                        bootstrap.setOption("keepAlive", true);
                        bootstrap.setOption("connectTimeoutMillis", 10000);

                        // Set up the default event pipeline.
                        bootstrap.setPipelineFactory(
                                new NettyClientPipelineFactory(this, true, sessionTable,
                                authKey, macDummy.getMacLength(), manager, rl, signatureLength,
                                new ReentrantLock()));


                        //******* EDUARDO BEGIN **************//
                        // Start the connection attempt.
                        ChannelFuture future = bootstrap.connect(manager.getRemoteAddress(currV[i]));


                        //creates MAC stuff
                        Mac macSend = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                        macSend.init(authKey);
                        Mac macReceive = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                        macReceive.init(authKey);
                        NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, currV[i], manager.getStaticConf().getRSAPublicKey(currV[i]), new ReentrantLock());
                        sessionTable.put(currV[i], cs);

                        System.out.println("Connecting to replica " + currV[i] + " at " + manager.getRemoteAddress(currV[i]));
                        //******* EDUARDO END **************//

                        future.awaitUninterruptibly();

                    } catch (InvalidKeyException ex) {
                        ex.printStackTrace();
                    }
                    rl.writeLock().unlock();
                } else {
                    rl.readLock().unlock();
                }
            }
        } catch (NoSuchAlgorithmException ex) {
        }
        //close connections with removed servers
        //ANB: This code need to be tested!!!
        
        //EDUARDO: The client takes a lot of time to close the channel, I suggest
        //a different approac (close later by a different thread or something like that),
        //maybe by the server!
        
        /*ListIterator ids = new LinkedList(sessionTable.keySet()).listIterator();
        while (ids.hasNext()) {
            int id = (Integer) ids.next();

            boolean found = false;
            for (int v : currV) {
                if (v == id) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                //System.out.println("Going to close channel to " + id);
                
                NettyClientServerSession cs =
                        (NettyClientServerSession) sessionTable.remove(id);
                cs.getChannel().close();
                //System.out.println("Channel closed " + id);
            }
        }*/


    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
        if (!(e.getCause() instanceof ClosedChannelException) && !(e.getCause() instanceof ConnectException)) {
            System.out.println("Connection with server closed.");
        } else {
            e.getCause().printStackTrace(System.err);
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        TOMMessage sm = (TOMMessage) e.getMessage();
        //delivers message to replyReceived callback
        trr.replyReceived(sm);
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        System.out.println("Channel connected");
    }

    @Override
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        if (this.closed) {
            return;
        }

        try {
            //sleeps 10 seconds before trying to reconnect
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
        }

        rl.writeLock().lock();
        //Iterator sessions = sessionTable.values().iterator();

        ArrayList<NettyClientServerSession> sessions = new ArrayList<NettyClientServerSession>(sessionTable.values());
        for (NettyClientServerSession ncss : sessions) {

            if (ncss.getChannel() == ctx.getChannel()) {
                try {

                    //******* EDUARDO BEGIN **************//
                    Mac macDummy = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());
                    // Configure the client.
                    ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable, authKey, macDummy.getMacLength(), manager, rl, TOMUtil.getSignatureSize(manager), new ReentrantLock()));
                    // Start the connection attempt.
                    if (manager.getRemoteAddress(ncss.getReplicaId()) != null) {

                        ChannelFuture future = bootstrap.connect(manager.getRemoteAddress(ncss.getReplicaId()));
                        //******* EDUARDO END **************//


                        //creates MAC stuff
                        Mac macSend = ncss.getMacSend();
                        Mac macReceive = ncss.getMacReceive();
                        NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, ncss.getReplicaId(), manager.getStaticConf().getRSAPublicKey(ncss.getReplicaId()), new ReentrantLock());
                        sessionTable.remove(ncss.getReplicaId());
                        sessionTable.put(ncss.getReplicaId(), cs);
                        //System.out.println("RE-Connecting to replica "+ncss.getReplicaId()+" at " + conf.getRemoteAddress(ncss.getReplicaId()));

                    } else {
                        // This cleans an olde server from the session table
                        sessionTable.remove(ncss.getReplicaId());
                    }
                } catch (NoSuchAlgorithmException ex) {
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
                Logger.println("Impossible to serialize message: " + sm);
            } finally {
                try {
                    dos.close();
                } catch (IOException ex) {
                }
            }
        }

        //Logger.println("Sending message with "+sm.serializedMessage.length+" bytes of content.");

        //produce signature
        if (sign && sm.serializedMessageSignature == null) {
            sm.serializedMessageSignature = signMessage(
                    manager.getStaticConf().getRSAPrivateKey(), sm.serializedMessage);
        }

        int sent = 0;
        for (int i = targets.length - 1; i >= 0; i--) {
            sm.destination = targets[i];

            rl.readLock().lock();
            Channel channel = ((NettyClientServerSession) sessionTable.get(targets[i])).getChannel();
            rl.readLock().unlock();
            if (channel.isConnected()) {
                sm.signed = sign;
                channel.write(sm);
                sent++;
            } else {
                //System.out.println("Channel to " + targets[i] + " is not connected");
                Logger.println("Channel to " + targets[i] + " is not connected");
            }

        }

        if (sent < manager.getCurrentViewF() + 1) {
            //if less than f+1 servers are connected send an exception to the client
            throw new RuntimeException("Impossible to connect to servers!");
        }
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
        } finally {
            try {
                dos.close();
            } catch (IOException ex) {
            }
        }

        //******* EDUARDO BEGIN **************//
        //produce signature
        byte[] data2 = signMessage(manager.getStaticConf().getRSAPrivateKey(), data);
        //******* EDUARDO END **************//

        sm.serializedMessageSignature = data2;
    }

    public byte[] signMessage(PrivateKey key, byte[] message) {
        //long startTime = System.nanoTime();
        try {
            if (signatureEngine == null) {
                signatureEngine = Signature.getInstance("SHA1withRSA");
            }
            byte[] result = null;

            signatureEngine.initSign(key);
            signatureEngine.update(message);
            result = signatureEngine.sign();

            //st.store(System.nanoTime() - startTime);
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void close() {
        this.closed = true;
        //Iterator sessions = sessionTable.values().iterator();
        rl.readLock().lock();
        ArrayList<NettyClientServerSession> sessions = new ArrayList<NettyClientServerSession>(sessionTable.values());
        rl.readLock().unlock();
        for (NettyClientServerSession ncss : sessions) {

            ncss.getChannel().close();
        }
    }
}
