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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author alysson
 */

/**
 * Tulio A. Ribeiro.
 * 
 * Generate a KeyPair used by SSL/TLS connections. Note that keypass argument is
 * equal to the variable SECRET.
 * 
 * The command generates the secret key.*/ 
//## Elliptic Curve 
  //$keytool -genkey -keyalg EC -alias bftsmartEC -keypass MySeCreT_2hMOygBwY -keystore ./ecKeyPair -dname "CN=BFT-SMaRT" 
  //$keytool -importkeystore -srckeystore ./ecKeyPair -destkeystore ./ecKeyPair -deststoretype pkcs12

//## RSA 
  //$keytool -genkey -keyalg RSA -keysize 2048 -alias bftsmartRSA -keypass MySeCreT_2hMOygBwY -keystore ./RSA_KeyPair_2048.pkcs12 -dname "CN=BFT-SMaRT"
  //$keytool -importkeystore -srckeystore ./RSA_KeyPair_2048.pkcs12 -destkeystore ./RSA_KeyPair_2048.pkcs12 -deststoretype pkcs12
 

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
    private List<PendingConnection> pendingConn = new LinkedList<PendingConnection>();
    private ServiceReplica replica;
    
    
    /**
	 * Tulio A. Ribeiro
	 * SSL / TLS.
	 */

	private KeyManagerFactory kmf;
	private KeyStore ks;
	private TrustManagerFactory trustMgrFactory;
	private SSLContext context;
	private SSLServerSocketFactory serverSocketFactory;
	private static final String SECRET = "MySeCreT_2hMOygBwY";
	private SecretKey selfPwd;
	private SSLServerSocket serverSocketSSLTLS;
	private String ssltlsProtocolVersion;

    public ServersCommunicationLayer(ServerViewController controller,
            LinkedBlockingQueue<SystemMessage> inQueue, 
            ServiceReplica replica) throws Exception {

        this.controller = controller;
        this.inQueue = inQueue;
        this.me = controller.getStaticConf().getProcessId();
        this.replica = replica;
        this.ssltlsProtocolVersion = controller.getStaticConf().getSSLTLSProtocolVersion();

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

		FileInputStream fis = null;
		try {
			fis = new FileInputStream("config/keysSSL_TLS/" + controller.getStaticConf().getSSLTLSKeyStore());
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(fis, SECRET.toCharArray());
		} finally {
			if (fis != null) {
				fis.close();
			}
		}

		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		kmf = KeyManagerFactory.getInstance(algorithm);
		kmf.init(ks, SECRET.toCharArray());

		trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
		trustMgrFactory.init(ks);

		context = SSLContext.getInstance(this.ssltlsProtocolVersion);
		context.init(kmf.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());

		serverSocketFactory = context.getServerSocketFactory();
		this.serverSocketSSLTLS = (SSLServerSocket) serverSocketFactory.createServerSocket(myPort, 100,
				InetAddress.getByName(myAddress));

		serverSocketSSLTLS.setEnabledCipherSuites(this.controller.getStaticConf().getEnabledCiphers());

		String[] ciphers = serverSocketFactory.getSupportedCipherSuites();
		for (int i = 0; i < ciphers.length; i++) {
			logger.trace("Supported Cipher: {} ", ciphers[i]);
		}

		//serverSocketSSLTLS.setPerformancePreferences(0, 2, 1);
		//serverSocketSSLTLS.setSoTimeout(connectionTimeoutMsec);
		serverSocketSSLTLS.setEnableSessionCreation(true);
		serverSocketSSLTLS.setReuseAddress(true);
		serverSocketSSLTLS.setNeedClientAuth(true);
		serverSocketSSLTLS.setWantClientAuth(true);
		

		SecretKeyFactory fac = TOMUtil.getSecretFactory();
		PBEKeySpec spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
		selfPwd = fac.generateSecret(spec);
        
      //Try connecting if a member of the current view. Otherwise, wait until the Join has been processed!
        if (controller.isInCurrentView()) {
            int[] initialV = controller.getCurrentViewAcceptors();
            for (int i = 0; i < initialV.length; i++) {
                if (initialV[i] != me) {
                    getConnection(initialV[i]);
                }
            }
        }
        
        start();
    }

    public SecretKey getSecretKey(int id) {
        if (id == controller.getStaticConf().getProcessId()) 
        	return selfPwd;
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
            ret = new ServerConnection(controller, null, 
            		remoteId, this.inQueue, this.replica);
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
        /*Tulio A. Ribeiro*/
        Integer[] targetsShuffled = Arrays.stream( targets ).boxed().toArray( Integer[]::new );
        Collections.shuffle(Arrays.asList(targetsShuffled), new Random(System.nanoTime())); 

        for (int target : targetsShuffled) {
			try {
				if (target == me) {
					sm.authenticated = true;
					inQueue.put(sm);
					logger.debug("Queueing (delivering) my own message, me:{}", target);
				} else {
					logger.debug("Sending message from:{} -> to:{}.", me,  target);
					getConnection(target).send(data);
				}
			} catch (InterruptedException ex) {
				logger.error("Interruption while inserting message into inqueue", ex);
			}
		}
    }

    public void shutdown() {
        
        logger.info("Shutting down replica sockets");
        
        doWork = false;

        //******* EDUARDO BEGIN **************//
        int[] activeServers = controller.getCurrentViewAcceptors();

        for (int i = 0; i < activeServers.length; i++) {
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

            	SSLSocket newSocket = (SSLSocket) serverSocketSSLTLS.accept();
				setSSLSocketOptions(newSocket);

                int remoteId = new DataInputStream(newSocket.getInputStream()).readInt();

                //******* EDUARDO BEGIN **************//
                if (!this.controller.isInCurrentView() &&
                     (this.controller.getStaticConf().getTTPId() != remoteId)) {
                    waitViewLock.lock();
                    pendingConn.add(new PendingConnection(newSocket, remoteId));
                    waitViewLock.unlock();
                } else {
					logger.debug("Trying establish connection with Replica: {}", remoteId);
                    establishConnection(newSocket, remoteId);
                }
                //******* EDUARDO END **************//

            } catch (SocketTimeoutException ex) {
				logger.trace("Server socket timed out, retrying");
			} catch (SSLHandshakeException sslex) {
				sslex.printStackTrace();
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
    private void establishConnection(SSLSocket newSocket, int remoteId) throws IOException {
        if ((this.controller.getStaticConf().getTTPId() == remoteId) || this.controller.isCurrentViewMember(remoteId)) {
            connectionsLock.lock();
            if (this.connections.get(remoteId) == null) { //This must never happen!!!
                //first time that this connection is being established
                //System.out.println("THIS DOES NOT HAPPEN....."+remoteId);
                this.connections.put(remoteId,
                			new ServerConnection(controller, newSocket, remoteId, inQueue, replica));
            } else {
                //reconnection	
            	logger.debug("ReConnecting with replica: {}", remoteId);
                this.connections.get(remoteId).reconnect(newSocket);
            }
            connectionsLock.unlock();

        } else {
			logger.debug("Closing connection with replica: {}", remoteId);
            newSocket.close();
        }
    }
    //******* EDUARDO END **************//

    public static void setSSLSocketOptions(SSLSocket socket) {
		try {
			socket.setTcpNoDelay(true);
		} catch (SocketException ex) {
			LoggerFactory.getLogger(ServersCommunicationLayer.class).
			error("Failed to set TCPNODELAY", ex);
		}
	}
    
    public static void setSocketOptions(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
        } catch (SocketException ex) {
            LoggerFactory.getLogger(ServersCommunicationLayer.class)
            .error("Failed to set TCPNODELAY", ex);
        }
    }

    @Override
    public String toString() {
        String str = "inQueue=" + inQueue.toString();
        int[] activeServers = controller.getCurrentViewAcceptors();
        for (int i = 0; i < activeServers.length; i++) {
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

        public SSLSocket s;
        public int remoteId;

        public PendingConnection(SSLSocket s, int remoteId) {
            this.s = s;
            this.remoteId = remoteId;
        }
    }

    //******* EDUARDO END **************//
}
