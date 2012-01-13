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
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipelineCoverage;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

/**
 *
 * @author Paulo
 */
@ChannelPipelineCoverage("all")
public class NettyClientServerCommunicationSystemServerSide extends SimpleChannelHandler implements CommunicationSystemServerSide {

    private static final String PASSWORD = "newcs";
    private RequestReceiver requestReceiver;
    private HashMap sessionTable;
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    //private List<TOMMessage> requestsReceived = Collections.synchronizedList(new ArrayList<TOMMessage>());
    //private ReentrantLock lock = new ReentrantLock();
    private ServerViewManager manager;

    public NettyClientServerCommunicationSystemServerSide(ServerViewManager manager) {
        try {
            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);

            this.manager = manager;
            sessionTable = new HashMap();
            rl = new ReentrantReadWriteLock();

            //Configure the server.
            /* Cached thread pool */
            ServerBootstrap bootstrap = new ServerBootstrap(
                    new NioServerSocketChannelFactory(
                    Executors.newCachedThreadPool(),
                    Executors.newCachedThreadPool()));

            //******* EDUARDO BEGIN **************//
            Mac macDummy = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());

            bootstrap.setOption("tcpNoDelay", true);
            bootstrap.setOption("keepAlive", true);

            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.keepAlive", true);

            //Set up the default event pipeline.
            bootstrap.setPipelineFactory(new NettyServerPipelineFactory(this, false, sessionTable, authKey, macDummy.getMacLength(), manager, rl, TOMUtil.getSignatureSize(manager), new ReentrantLock()));

            //Bind and start to accept incoming connections.
            bootstrap.bind(new InetSocketAddress(manager.getStaticConf().getHost(
                    manager.getStaticConf().getProcessId()),
                    manager.getStaticConf().getPort(manager.getStaticConf().getProcessId())));

            System.out.println("#Bound to port " + manager.getStaticConf().getPort(manager.getStaticConf().getProcessId()));
            System.out.println("#myId " + manager.getStaticConf().getProcessId());
            System.out.println("#n " + manager.getCurrentViewN());
            System.out.println("#f " + manager.getCurrentViewF());
            System.out.println("#requestTimeout= " + manager.getStaticConf().getRequestTimeout());
            System.out.println("#maxBatch= " + manager.getStaticConf().getMaxBatchSize());
            System.out.println("#Using MACs = " + manager.getStaticConf().getUseMACs());
            System.out.println("#Using Signatures = " + manager.getStaticConf().getUseSignatures());
            //******* EDUARDO END **************//
        } catch (InvalidKeySpecException ex) {
        } catch (NoSuchAlgorithmException ex) {
        }
    }

    @Override
    public void exceptionCaught( ChannelHandlerContext ctx, ExceptionEvent e) {
        e.getCause().printStackTrace();
        if (!(e.getCause() instanceof ClosedChannelException) && !(e.getCause() instanceof ConnectException)) {
            System.out.println("Connection with client closed...");
        } else {
            e.getCause().printStackTrace(System.err);
            System.out.println("ExceptionCaught "+ctx.getName());
            
        }
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
        //delivers message to TOMLayer
        requestReceiver.requestReceived((TOMMessage) e.getMessage());
    }

    @Override
    public void channelConnected( ChannelHandlerContext ctx, ChannelStateEvent e) {
        Logger.println("Session Created, active clients=" + sessionTable.size());
        System.out.println("Session Created, active clients=" + sessionTable.size());
    }

    @Override
    public void channelClosed( ChannelHandlerContext ctx, ChannelStateEvent e) {
        rl.writeLock().lock();
        try {
            Set s = sessionTable.entrySet();
            Iterator i = s.iterator();
            while (i.hasNext()) {
                Entry m = (Entry) i.next();
                NettyClientServerSession value = (NettyClientServerSession) m.getValue();
                if (e.getChannel().equals(value.getChannel())) {
                    int key = (Integer) m.getKey();
                    sessionTable.remove(key);
                    System.out.println("#Removed client channel with ID= " + key);
                    System.out.println("#active clients=" + sessionTable.size());
                    break;
                }
            }
        } finally {
            rl.writeLock().unlock();
        }
        Logger.println("Session Closed, active clients=" + sessionTable.size());
    }

    @Override
    public void setRequestReceiver(RequestReceiver tl) {
        this.requestReceiver = tl;
    }

    private ReentrantLock sendLock = new ReentrantLock();
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
            Logger.println("Error enconding message.");
        } finally {
            try {
                dos.close();
            } catch (IOException ex) {
                Logger.println("Exception closing DataOutputStream: " + ex.getMessage());
            }
        }

        //replies are not signed in the current JBP version
        sm.signed = false;
        //produce signature if necessary (never in the current version)
        if (sm.signed) {
            //******* EDUARDO BEGIN **************//
            byte[] data2 = TOMUtil.signMessage(manager.getStaticConf().getRSAPrivateKey(), data);
            //******* EDUARDO END **************//
            sm.serializedMessageSignature = data2;
        }

        for (int i = 0; i < targets.length; i++) {
            
            rl.readLock().lock();
            NettyClientServerSession ncss = (NettyClientServerSession) sessionTable.get(targets[i]);
            if (ncss != null) {
                Channel session = ncss.getChannel();
                rl.readLock().unlock();
                sm.destination = targets[i];
                //send message
                sendLock.lock();
                session.write(sm); // This used to invoke "await". Removed to avoid blockage and race condition.
                sendLock.unlock();
            } else {
                rl.readLock().unlock();
            }
        }
    }
}
