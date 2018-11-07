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
package bftsmart.communication.server;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.SystemMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.util.TOMUtil;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author alysson
 */
public class ServersCommunicationLayer extends Thread {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());


    private ServerViewController controller;
    private LinkedBlockingQueue<SystemMessage> inQueue;
    private HashMap<Integer, ServerConnection> connections = new HashMap<>();
    private ServerSocket serverSocket;
    private int me;
    private boolean doWork = true;
    private Lock connectionsLock = new ReentrantLock();
    private ReentrantLock waitViewLock = new ReentrantLock();
    //private Condition canConnect = waitViewLock.newCondition();
    private List<PendingConnection> pendingConn = new LinkedList<PendingConnection>();
    private ServiceReplica replica;
    private SecretKey selfPwd;
    private static final String PASSWORD = "commsyst";

    public ServersCommunicationLayer(ServerViewController controller,
            LinkedBlockingQueue<SystemMessage> inQueue, ServiceReplica replica) throws Exception {

        this.controller = controller;
        this.inQueue = inQueue;
        this.me = controller.getStaticConf().getProcessId();
        this.replica = replica;

        //Try connecting if a member of the current view. Otherwise, wait until the Join has been processed!
        if (controller.isInCurrentView()) {
            int[] initialV = controller.getCurrentViewAcceptors();
            for (int i = 0; i < initialV.length; i++) {
                if (initialV[i] != me) {
                    getConnection(initialV[i]);
                }
            }
        }

        String myAddress;
        String confAddress =
                    controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId()).getAddress().getHostAddress();
        
        if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {
                            
            myAddress = InetAddress.getLoopbackAddress().getHostAddress();

            }

        else if (controller.getStaticConf().getBindAddress().equals("")) {

            myAddress = InetAddress.getLocalHost().getHostAddress();

            //If the replica binds to the loopback address, clients will not be able to connect to replicas.
            //To solve that issue, we bind to the address supplied in config/hosts.config instead.
            if (InetAddress.getLoopbackAddress().getHostAddress().equals(myAddress) && !myAddress.equals(confAddress)) {

                myAddress = confAddress;
            }


        } else {

            myAddress = controller.getStaticConf().getBindAddress();
        }
        
        int myPort = controller.getStaticConf().getServerToServerPort(controller.getStaticConf().getProcessId());
                        
        serverSocket = new ServerSocket(myPort, 50, InetAddress.getByName(myAddress));

        /*serverSocket = new ServerSocket(controller.getStaticConf().getServerToServerPort(
                controller.getStaticConf().getProcessId()));*/

        SecretKeyFactory fac = TOMUtil.getSecretFactory();
        PBEKeySpec spec = TOMUtil.generateKeySpec(PASSWORD.toCharArray());
        selfPwd = fac.generateSecret(spec);

        serverSocket.setSoTimeout(10000);
        serverSocket.setReuseAddress(true);

        start();
    }

    public SecretKey getSecretKey(int id) {
        if (id == controller.getStaticConf().getProcessId()) return selfPwd;
        else return connections.get(id).getSecretKey();
    }

    //******* EDUARDO BEGIN **************//
    public void updateConnections() {
        connectionsLock.lock();

        if (this.controller.isInCurrentView()) {

            Iterator<Integer> it = this.connections.keySet().iterator();
            List<Integer> toRemove = new LinkedList<Integer>();
            while (it.hasNext()) {
                int rm = it.next();
                if (!this.controller.isCurrentViewMember(rm)) {
                    toRemove.add(rm);
                }
            }
            for (int i = 0; i < toRemove.size(); i++) {
                this.connections.remove(toRemove.get(i)).shutdown();
            }

            int[] newV = controller.getCurrentViewAcceptors();
            for (int i = 0; i < newV.length; i++) {
                if (newV[i] != me) {
                    getConnection(newV[i]);
                }
            }
        } else {

            Iterator<Integer> it = this.connections.keySet().iterator();
            while (it.hasNext()) {
                this.connections.get(it.next()).shutdown();
            }
        }

        connectionsLock.unlock();
    }

    private ServerConnection getConnection(int remoteId) {
        connectionsLock.lock();
        ServerConnection ret = this.connections.get(remoteId);
        if (ret == null) {
            ret = new ServerConnection(controller, null, remoteId, this.inQueue, this.replica);
            this.connections.put(remoteId, ret);
        }
        connectionsLock.unlock();
        return ret;
    }
    //******* EDUARDO END **************//


    public final void send(int[] targets, SystemMessage sm, boolean useMAC) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
        try {
            new ObjectOutputStream(bOut).writeObject(sm);
        } catch (IOException ex) {
            logger.error("Failed to serialize message", ex);
        }

        byte[] data = bOut.toByteArray();
        
        // this shuffling is done to prevent the replica with the lowest ID/index  from being always
        // the last one receiving the messages, which can result in that replica  to become consistently
        // delayed in relation to the others.
        Integer[] targetsShuffled = Arrays.stream( targets ).boxed().toArray( Integer[]::new );
        Collections.shuffle(Arrays.asList(targetsShuffled), new Random(System.nanoTime())); 

        for (int i : targetsShuffled) {
            try {
                if (i == me) {
                    sm.authenticated = true;
                    inQueue.put(sm);
                } else {
                    //System.out.println("Going to send message to: "+i);
                    //******* EDUARDO BEGIN **************//
                    //connections[i].send(data);
                    getConnection(i).send(data, useMAC);
                    //******* EDUARDO END **************//
                }
            } catch (InterruptedException ex) {
                logger.error("Interruption while inserting message into inqueue",ex);
            }
        }
    }

    public void shutdown() {
        
        logger.info("Shutting down replica sockets");
        
        doWork = false;

        //******* EDUARDO BEGIN **************//
        int[] activeServers = controller.getCurrentViewAcceptors();

        for (int i = 0; i < activeServers.length; i++) {
            //if (connections[i] != null) {
            //  connections[i].shutdown();
            //}
            if (me != activeServers[i]) {
                getConnection(activeServers[i]).shutdown();
            }
        }
    }

    //******* EDUARDO BEGIN **************//
    public void joinViewReceived() {
        waitViewLock.lock();
        for (int i = 0; i < pendingConn.size(); i++) {
            PendingConnection pc = pendingConn.get(i);
            try {
                establishConnection(pc.s, pc.remoteId);
            } catch (Exception e) {
                logger.error("Failed to estabilish connection to " + pc.remoteId,e);
            }
        }

        pendingConn.clear();

        waitViewLock.unlock();
    }
    //******* EDUARDO END **************//

    @Override
    public void run() {
        while (doWork) {
            try {

                //System.out.println("Waiting for server connections");

                Socket newSocket = serverSocket.accept();

                ServersCommunicationLayer.setSocketOptions(newSocket);
                int remoteId = new DataInputStream(newSocket.getInputStream()).readInt();

                //******* EDUARDO BEGIN **************//
                if (!this.controller.isInCurrentView() &&
                     (this.controller.getStaticConf().getTTPId() != remoteId)) {
                    waitViewLock.lock();
                    pendingConn.add(new PendingConnection(newSocket, remoteId));
                    waitViewLock.unlock();
                } else {
                    establishConnection(newSocket, remoteId);
                }
                //******* EDUARDO END **************//

            } catch (SocketTimeoutException ex) {
            
                logger.debug("Server socket timed out, retrying");
            } catch (IOException ex) {
                logger.error("Problem during thread execution", ex);
            }
        }

        try {
            serverSocket.close();
        } catch (IOException ex) {
            logger.error("Failed to close server socket", ex);
        }

        logger.info("ServerCommunicationLayer stopped.");
    }

    //******* EDUARDO BEGIN **************//
    private void establishConnection(Socket newSocket, int remoteId) throws IOException {
        if ((this.controller.getStaticConf().getTTPId() == remoteId) || this.controller.isCurrentViewMember(remoteId)) {
            connectionsLock.lock();
            //System.out.println("Vai se conectar com: "+remoteId);
            if (this.connections.get(remoteId) == null) { //This must never happen!!!
                //first time that this connection is being established
                //System.out.println("THIS DOES NOT HAPPEN....."+remoteId);
                this.connections.put(remoteId, new ServerConnection(controller, newSocket, remoteId, inQueue, replica));
            } else {
                //reconnection
                this.connections.get(remoteId).reconnect(newSocket);
            }
            connectionsLock.unlock();

        } else {
            //System.out.println("Closing connection of: "+remoteId);
            newSocket.close();
        }
    }
    //******* EDUARDO END **************//

    public static void setSocketOptions(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
        } catch (SocketException ex) {
            
            LoggerFactory.getLogger(ServersCommunicationLayer.class).error("Failed to set TCPNODELAY", ex);
        }
    }

    @Override
    public String toString() {
        String str = "inQueue=" + inQueue.toString();

        int[] activeServers = controller.getCurrentViewAcceptors();

        for (int i = 0; i < activeServers.length; i++) {

            //for(int i=0; i<connections.length; i++) {
            // if(connections[i] != null) {
            if (me != activeServers[i]) {
                str += ", connections[" + activeServers[i] + "]: outQueue=" + getConnection(activeServers[i]).outQueue;
            }
        }

        return str;
    }


    //******* EDUARDO BEGIN: List entry that stores pending connections,
    // as a server may accept connections only after learning the current view,
    // i.e., after receiving the response to the join*************//
    // This is for avoiding that the server accepts connectsion from everywhere
    public class PendingConnection {

        public Socket s;
        public int remoteId;

        public PendingConnection(Socket s, int remoteId) {
            this.s = s;
            this.remoteId = remoteId;
        }
    }

    //******* EDUARDO END **************//
}
