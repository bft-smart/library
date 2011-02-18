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
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.communication.client.CommunicationSystemClientSide;
import navigators.smart.communication.client.ReplyReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;


/**
 *
 * @author Paulo
 */
@ChannelPipelineCoverage("all")
public class NettyClientServerCommunicationSystemClientSide extends SimpleChannelUpstreamHandler implements CommunicationSystemClientSide {

//    private static final int MAGIC = 59;
//    private static final int CONNECT_TIMEOUT = 3000;
    private static final String PASSWORD = "newcs";
    //private static final int BENCHMARK_PERIOD = 10000;
    protected ReplyReceiver trr;
    private TOMConfiguration conf;
    private Hashtable<Integer, NettyClientServerSession> sessionTable;
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    //the signature engine used in the system
    private Signature signatureEngine;
    //private Storage st;
//    private int count = 0;
    private int signatureLength;    

    public NettyClientServerCommunicationSystemClientSide(TOMConfiguration conf) {
        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);

            this.conf = conf;
            this.sessionTable = new Hashtable<Integer, NettyClientServerSession>();
            //this.st = new Storage(BENCHMARK_PERIOD);
            this.rl = new ReentrantReadWriteLock();
            Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
            signatureLength = TOMUtil.getSignatureSize();
            for (int i = 0; i < conf.getN(); i++) {
                try {
                    // Configure the client.
                    ClientBootstrap bootstrap = new ClientBootstrap(
                            new NioClientSocketChannelFactory(
                            Executors.newCachedThreadPool(),
                            Executors.newCachedThreadPool()));

                    bootstrap.setOption("tcpNoDelay", true);
                    bootstrap.setOption("keepAlive", true);

                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable, authKey, macDummy.getMacLength(), conf, rl, signatureLength, new ReentrantLock()));

                    // Start the connection attempt.
                    ChannelFuture future = bootstrap.connect(conf.getRemoteAddress(i));

                    //creates MAC stuff
                    Mac macSend = Mac.getInstance(conf.getHmacAlgorithm());
                    macSend.init(authKey);
                    Mac macReceive = Mac.getInstance(conf.getHmacAlgorithm());
                    macReceive.init(authKey);
                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, i, TOMConfiguration.getRSAPublicKey(i), new ReentrantLock());
                    sessionTable.put(i, cs);

                    System.out.println("Connecting to replica " + i + " at " + conf.getRemoteAddress(i));
                    future.awaitUninterruptibly();


                } catch (InvalidKeyException ex) {
                    Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {

        if (!(e.getCause() instanceof ClosedChannelException) && !(e.getCause() instanceof ConnectException)) {
            e.getCause().printStackTrace();
        }
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        //System.out.println("MsgReceived");
        TOMMessage sm = (TOMMessage) e.getMessage();

        //delivers message to replyReceived callback
        trr.replyReceived(sm);
    }

    @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
    	Channel ch = e.getChannel();
    	ChannelBuffer id = ChannelBuffers.buffer(4);
    	id.writeInt(conf.getProcessId());
    	ChannelFuture f = ch.write(id);
    	f.awaitUninterruptibly();
        System.out.println("Channel connected");
    }

    @Override
    public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        try {
            //sleeps 10 seconds before trying to reconnect
            Thread.sleep(10000);
        } catch (InterruptedException ex) {
            Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
        }
        //System.out.println("Channel closed");
        rl.writeLock().lock();
        //tries to reconnect the channel
        Enumeration<NettyClientServerSession> sessionElements = sessionTable.elements();
        while (sessionElements.hasMoreElements()) {
            NettyClientServerSession ncss = sessionElements.nextElement();
            if (ncss.getChannel() == ctx.getChannel()) {
                try {
                    Mac macDummy = Mac.getInstance(conf.getHmacAlgorithm());
                    // Configure the client.                    
                    ClientBootstrap bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));
                    // Set up the default event pipeline.
                    bootstrap.setPipelineFactory(new NettyClientPipelineFactory(this, true, sessionTable, authKey, macDummy.getMacLength(), conf, rl, TOMUtil.getSignatureSize(), new ReentrantLock()));
                    // Start the connection attempt.
                    ChannelFuture future = bootstrap.connect(conf.getRemoteAddress(ncss.getReplicaId()));
                    //creates MAC stuff
                    Mac macSend = ncss.getMacSend();
                    Mac macReceive = ncss.getMacReceive();
                    NettyClientServerSession cs = new NettyClientServerSession(future.getChannel(), macSend, macReceive, ncss.getReplicaId(), TOMConfiguration.getRSAPublicKey(ncss.getReplicaId()), new ReentrantLock());
                    sessionTable.remove(ncss.getReplicaId());
                    sessionTable.put(ncss.getReplicaId(), cs);
                    //System.out.println("RE-Connecting to replica "+ncss.getReplicaId()+" at " + conf.getRemoteAddress(ncss.getReplicaId()));
                } catch (NoSuchAlgorithmException ex) {
                    Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        }

        //closes all other channels to avoid messages being sent to only a subset of the replicas
		/*
		 * Enumeration sessionElements = sessionTable.elements(); while
		 * (sessionElements.hasMoreElements()){ ((NettyClientServerSession)
		 * sessionElements.nextElement()).getChannel().close(); }
		 */
        rl.writeLock().unlock();
    }

    public void setReplyReceiver(ReplyReceiver trr) {
        this.trr = trr;
    }

    public void send(boolean sign, int[] targets, TOMMessage sm) {
    	if(sign){
    		//checks if msg is serialized and signs it then
    		sign(sm);
    	} else {
    		//check only needed when not signing
    		checkSerialized(sm);
    	}

        for (int i = targets.length - 1; i >= 0; i--) {
			/*
			 * *********************************************************
			 * *******************MALICIOUS CODE************************
			 * ********************************************************* 
			 * //don't send the message to server 0 if my id is 5 
			 * if (conf.getProcessId() == 5 && (i == 0)) { continue; }
			 * 
			 * *********************************************************
			 * *********************************************************
			 * *********************************************************
			 */
            writeToChannel(sm,targets[i]);
        }
/*
        //statistics about signature execution time
        count++;
        if (count % BENCHMARK_PERIOD == 0) {
            int myId = conf.getProcessId();
            System.out.println("--Signature benchmark:--");
            System.out.println("(" + myId + ")Average time for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getAverage(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Standard desviation for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getDP(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Average time for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getAverage(false) / 1000 + " us ");
            System.out.println("(" + myId + ")Standard desviation for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getDP(false) / 1000 + " us ");
            System.out.println("(" + myId + ")Maximum time for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getMax(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Maximum time for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getMax(false) / 1000 + " us ");
            System.out.println("(" + myId + ")----------------------------------------------------------------------");
            count = 0;
            st.reset();
        }
 	*/
    }
    public void send(boolean sign, int target, TOMMessage sm) {
    	if(sign){
    		//checks if msg is serialized and signs it then
    		sign(sm);
    	} else {
    		//check only needed when not signing
    		checkSerialized(sm);
    	}
    	writeToChannel(sm,target);
    }

    private void writeToChannel(TOMMessage sm, int destination) {
    	sm.destination = destination;
        rl.readLock().lock();
        Channel channel = sessionTable.get(destination).getChannel();
        rl.readLock().unlock();
        if (channel.isConnected()) {
            channel.write(sm);
        } else {
            //System.out.println("WARNING: channel is not connected");
        }
		
	}

	private void checkSerialized(TOMMessage sm) {
    	//check serialized message
        if (sm.serializedMessage == null) {
            sm.serializedMessage = serialize(sm);
        }
	}

	public void sign(TOMMessage sm) {
		checkSerialized(sm);

        //produce signature        
        byte[] data2 = signMessage(TOMConfiguration.getRSAPrivateKey(), sm.serializedMessage);
        sm.signed = true;
        sm.serializedMessageSignature = data2;
/*
        //statistics about signature execution time
        count++;
        if (count % BENCHMARK_PERIOD == 0) {
            int myId = conf.getProcessId();
            System.out.println("--Signature benchmark:--");
            System.out.println("(" + myId + ")Average time for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getAverage(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Standard desviation for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getDP(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Average time for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getAverage(false) / 1000 + " us ");
            System.out.println("(" + myId + ")Standard desviation for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getDP(false) / 1000 + " us ");
            System.out.println("(" + myId + ")Maximum time for " + BENCHMARK_PERIOD + " signatures (-10%) = " + this.st.getMax(true) / 1000 + " us ");
            System.out.println("(" + myId + ")Maximum time for " + BENCHMARK_PERIOD + " signatures (all samples) = " + this.st.getMax(false) / 1000 + " us ");
            System.out.println("(" + myId + ")----------------------------------------------------------------------");
            count = 0;
            st.reset();
        }
*/
    }

    public byte[] signMessage(PrivateKey key, byte[] message) {
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

    private byte[] serialize(TOMMessage sm) {
        //serialize message
        DataOutputStream oos = null;

        byte[] data = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            oos = new DataOutputStream(baos);
            sm.serialise(oos);
            oos.flush();
            data = baos.toByteArray();
            return data;
        } catch (IOException ex) {
            Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }
}
