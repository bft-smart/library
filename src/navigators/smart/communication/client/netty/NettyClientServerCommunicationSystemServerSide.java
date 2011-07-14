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
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.communication.server.ServerConnection;
import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMUtil;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
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
public class NettyClientServerCommunicationSystemServerSide extends SimpleChannelHandler implements CommunicationSystemServerSide  {

     /**
     * number of measures used to calculate statistics
     */
    //private final int BENCHMARK_PERIOD = 100000;

    private static final String PASSWORD = "newcs";
    private RequestReceiver requestReceiver;
    private HashMap sessionTable;
    private ReentrantReadWriteLock rl;
    private SecretKey authKey;
    //private long numReceivedMsgs = 0;
    //private long lastMeasurementStart = 0;
    //private long max=0;
    private List<TOMMessage> requestsReceived = Collections.synchronizedList(new ArrayList<TOMMessage>());
    private ReentrantLock lock = new ReentrantLock();


    private ReconfigurationManager manager;

    public NettyClientServerCommunicationSystemServerSide(ReconfigurationManager manager) {
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

            /* Fixed thread pool
            ServerBootstrap bootstrap = new ServerBootstrap(
                new NioServerSocketChannelFactory(
                        Executors.newFixedThreadPool(conf.getNumberOfNIOThreads()),
                        Executors.newFixedThreadPool(conf.getNumberOfNIOThreads())));
            */

            //******* EDUARDO BEGIN **************//
            Mac macDummy = Mac.getInstance(manager.getStaticConf().getHmacAlgorithm());

            bootstrap.setOption("tcpNoDelay", true);
            bootstrap.setOption("keepAlive", true);

            bootstrap.setOption("child.tcpNoDelay", true);
            bootstrap.setOption("child.keepAlive", true);

            //Set up the default event pipeline.
            bootstrap.setPipelineFactory(new NettyServerPipelineFactory(this,false,sessionTable,authKey,macDummy.getMacLength(),manager,rl,TOMUtil.getSignatureSize(manager), new ReentrantLock() ));

            //Bind and start to accept incoming connections.
            bootstrap.bind(new InetSocketAddress( manager.getStaticConf().getHost(
                     manager.getStaticConf().getProcessId()),
                      manager.getStaticConf().getPort( manager.getStaticConf().getProcessId())));

            System.out.println("#Bound to port " + manager.getStaticConf().getPort(manager.getStaticConf().getProcessId()));
            System.out.println("#myId " + manager.getStaticConf().getProcessId());
            System.out.println("#n " + manager.getCurrentViewN());
            System.out.println("#f " + manager.getCurrentViewF());
            System.out.println("#requestTimeout= " +  manager.getStaticConf().getRequestTimeout());
            System.out.println("#maxBatch= " +  manager.getStaticConf().getMaxBatchSize());
            System.out.println("#Using MACs = " +  manager.getStaticConf().getUseMACs());
            System.out.println("#Using Signatures = " + manager.getStaticConf().getUseSignatures());
        //******* EDUARDO END **************//
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx, ExceptionEvent e) {
        //if (!(e.getCause() instanceof ClosedChannelException))
        System.out.println("Excepção no  CS (server side)!");
               e.getCause().printStackTrace();
    }

    @Override
    public void messageReceived(
            ChannelHandlerContext ctx, MessageEvent e) {
        TOMMessage sm = (TOMMessage) e.getMessage();

        //******* EDUARDO BEGIN **************//
        if (manager.getStaticConf().getCommBuffering()>0) {
            lock.lock();
            requestsReceived.add(sm);
            if (requestsReceived.size()>= manager.getStaticConf().getCommBuffering()){
        //******* EDUARDO END **************//
                for (int i=0; i<requestsReceived.size(); i++) {
                    //delivers message to TOMLayer
                    requestReceiver.requestReceived(requestsReceived.get(i));
                }
                requestsReceived = null;
                requestsReceived = new ArrayList<TOMMessage>();
            }
            lock.unlock();
        }
        else {
            //delivers message to TOMLayer
            requestReceiver.requestReceived(sm);
        }
        /*obtains and unlocks client lock (that guarantees one thread per client)
        rl.readLock().lock();
        Lock clientLock = ((NettyClientServerSession)sessionTable.get(sm.getSender())).getLock();
        int lastMsgReceived = ((NettyClientServerSession)sessionTable.get(sm.getSender())).getLastMsgReceived();
        if (sm.getSequence() != lastMsgReceived+1)
            System.out.println("(Netty message received) WARNING: Received request "+sm+" but last message received was "+lastMsgReceived);
        ((NettyClientServerSession)sessionTable.get(sm.getSender())).setLastMsgReceived(sm.getSequence());
        rl.readLock().unlock();
        clientLock.unlock();
         */
         /*
        lock.lock();
        numReceivedMsgs++;
        if (numReceivedMsgs == 1) {
            lastMeasurementStart = System.currentTimeMillis();
        } else if (numReceivedMsgs==BENCHMARK_PERIOD) {
            long elapsedTime = System.currentTimeMillis() - lastMeasurementStart;
            double opsPerSec_ = ((double)BENCHMARK_PERIOD)/(elapsedTime/1000.0);
            long opsPerSec = Math.round(opsPerSec_);
            if (opsPerSec>max)
                max = opsPerSec;
            System.out.println("(Netty messageReceived) Last "+BENCHMARK_PERIOD+" NETTY messages were received at a rate of " + opsPerSec + " msgs per second");
            System.out.println("(Netty messageReceived) Max NETTY through. until now: " + max + " ops per second");
            System.out.println("(Netty messageReceived) Active clients: " + sessionTable.size());
            numReceivedMsgs = 0;
        }
        lock.unlock();
        */
    }
/*
    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        //System.out.println("Message sent");
        TOMMessage sm = (TOMMessage) e.getMessage();
        long duration = System.nanoTime() - (Long)session.getAttribute("startInstant");
        int counter = (Integer) session.getAttribute("msgCount");
        session.setAttribute("msgCount",++counter);
        Storage st = (Storage) session.getAttribute("storage");
        if (counter>benchmarkPeriod/2){
            st.store(duration);
            session.setAttribute("storage",st);
        }

        if (st.getCount()==benchmarkPeriod/2){
                System.out.println("TOM delay: Average time for "+benchmarkPeriod/2+" executions (-10%) = "+st.getAverage(true)/1000+ " us ");
                System.out.println("TOM delay: Standard desviation for "+benchmarkPeriod/2+" executions (-10%) = "+st.getDP(true)/1000 + " us ");
                System.out.println("TOM delay: Average time for "+benchmarkPeriod/2+" executions (all samples) = "+st.getAverage(false)/1000+ " us ");
                System.out.println("TOM delay: Standard desviation for "+benchmarkPeriod/2+" executions (all samples) = "+st.getDP(false)/1000 + " us ");
                System.out.println("TOM delay: Maximum time for "+benchmarkPeriod/2+" executions (-10%) = "+st.getMax(true)/1000+ " us ");
                System.out.println("TOM delay: Maximum time for "+benchmarkPeriod/2+" executions (all samples) = "+st.getMax(false)/1000+ " us ");
                st = new Storage(benchmarkPeriod/2);
                session.setAttribute("storage",st);
                session.setAttribute("msgCount",0);
        }
    }
*/
     @Override
    public void channelConnected(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        navigators.smart.tom.util.Logger.println("Session Created, active clients="+sessionTable.size());
        //session.setAttribute("storage",st);
        //session.setAttribute("msgCount",0);

    }

    @Override
     public void channelClosed(
            ChannelHandlerContext ctx, ChannelStateEvent e) {
        rl.writeLock().lock();
        //removes session from sessionTable
        Set s = sessionTable.entrySet();
        Iterator i = s.iterator();
        while (i.hasNext()) {
            Map.Entry m = (Map.Entry) i.next();
            NettyClientServerSession value = (NettyClientServerSession) m.getValue();
            if (e.getChannel().equals(value.getChannel())) {
                int key = (Integer) m.getKey();
                sessionTable.remove(key);
                System.out.println("#Removed client channel with ID= " + key);
                System.out.println("#active clients="+sessionTable.size());
                break;
            }
        }
        rl.writeLock().unlock();
        navigators.smart.tom.util.Logger.println("Session Closed, active clients="+sessionTable.size());
    }

    public void setRequestReceiver(RequestReceiver tl) {
        this.requestReceiver = tl;
    }

    public void send(int[] targets, TOMMessage sm, boolean serializeClassHeaders) {
         //serialize message
            DataOutputStream dos = null;
            ObjectOutputStream oos = null;

            byte[] data = null;
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if (!serializeClassHeaders) {
                    dos = new DataOutputStream(baos);
                    sm.wExternal(dos);
                    dos.flush();
                    sm.includesClassHeader = false;
                }
                else {
                    oos = new ObjectOutputStream(baos);
                    oos.writeObject(sm);
                    oos.flush();
                    sm.includesClassHeader = true;
                }
                data = baos.toByteArray();
                sm.serializedMessage = data;
            } catch (IOException ex) {
                Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    if (dos != null) {
                        dos.close();
                    }
                    if (oos != null) {
                        oos.close();
                    }
                } catch (IOException ex) {
                    Logger.getLogger(NettyClientServerCommunicationSystemClientSide.class.getName()).log(Level.SEVERE, null, ex);
                }
            }


        //replies are not signed in the current JBP version
        sm.signed = false;
        //produce signature if necessary (never in the current version)
        if (sm.signed){
            //******* EDUARDO BEGIN **************//
            byte[] data2 = TOMUtil.signMessage( manager.getStaticConf().getRSAPrivateKey(), data);
            //******* EDUARDO END **************//
            sm.serializedMessageSignature = data2;
        }
        for (int i = 0; i < targets.length; i++) {
            rl.readLock().lock();
            NettyClientServerSession ncss = (NettyClientServerSession)sessionTable.get(targets[i]);
            if (ncss!=null){
                
                Channel session = ncss.getChannel();
                rl.readLock().unlock();
                sm.destination = targets[i];
                //send message
                ChannelFuture f = session.write(sm);
                try {
                    f.await();
                } catch (InterruptedException ex) {
                    Logger.getLogger(NettyClientServerCommunicationSystemServerSide.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            else
                rl.readLock().unlock();
        }
    }
}
