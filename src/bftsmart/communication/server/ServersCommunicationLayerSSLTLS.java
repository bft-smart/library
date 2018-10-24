/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, Tulio A. Ribeiro and the authors indicated in the @author tags

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.bouncycastle.jcajce.provider.asymmetric.ec.KeyFactorySpi.ECDSA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.communication.SystemMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.util.TOMUtil;

/**
 *
 * @author alysson
 */


/**
 * Tulio Ribeiro.
 * 
 * Generate a KeyPair used by SSL/TLS connections. 
 * Note that keypass argument is equal to the variable SECRET.
 * 
 * The command generates the secret key. 
 * ##Elliptic Curve 
 * $keytool -genkey -keyalg EC -alias bftsmartEC -keypass MySeCreT_2hMOygBwY  -keystore ./ecKeyPair -dname "CN=BFT-SMaRT" 
 * $keytool -importkeystore -srckeystore ./ecKeyPair -destkeystore ./ecKeyPair -deststoretype pkcs12
 * 
 * ##RSA
 * $keytool -genkey -keyalg RSA -keysize 2048 -alias bftsmartRSA -keypass MySeCreT_2hMOygBwY  -keystore ./RSA_KeyPair_2048.pkcs12 -dname "CN=BFT-SMaRT"
 * $keytool -importkeystore -srckeystore ./RSA_KeyPair_2048.pkcs12 -destkeystore ./RSA_KeyPair_2048.pkcs12 -deststoretype pkcs12
 */

public class ServersCommunicationLayerSSLTLS extends Thread {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private ServerViewController controller;
	private LinkedBlockingQueue<SystemMessage> inQueue;
	private HashMap<Integer, ServerConnectionSSLTLS> connections = new HashMap<>();
	
	private int me;
	private boolean doWork = true;
	private Lock connectionsLock = new ReentrantLock();
	private ReentrantLock waitViewLock = new ReentrantLock();
	private List<PendingConnection> pendingConn = new LinkedList<PendingConnection>();
	private ServiceReplica replica;

	
	/**
	 * Tulio A. Ribeiro
	 * 
	 * SSL / TLS
	 */
	
	private KeyManagerFactory kmf;
	private KeyStore ks;
	private FileInputStream fis;
	private TrustManagerFactory trustMgrFactory;
	private SSLContext context;
	private SSLServerSocketFactory serverSocketFactory;	
	private static final String SECRET = "MySeCreT_2hMOygBwY";
	private SecretKey selfPwd;
	private SSLServerSocket serverSocketSSLTLS;
	private String ssltlsProtocolVersion;

	public ServersCommunicationLayerSSLTLS(
				ServerViewController controller, 
				LinkedBlockingQueue<SystemMessage> inQueue,
				ServiceReplica replica) 
					throws 
						KeyStoreException, 
						NoSuchAlgorithmException, 
						CertificateException, 
						IOException, 
						UnrecoverableKeyException, 
						KeyManagementException, 
						InvalidKeySpecException  {

		this.controller = controller;
		this.inQueue = inQueue;
		this.me = controller.getStaticConf().getProcessId();
		this.replica = replica;
		this.ssltlsProtocolVersion = controller.getStaticConf().getSSLTLSProtocolVersion();

		String myAddress;
		String confAddress = controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId())
				.getAddress().getHostAddress();

		if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {

			myAddress = InetAddress.getLoopbackAddress().getHostAddress();

		}

		else if (controller.getStaticConf().getBindAddress().equals("")) {

			myAddress = InetAddress.getLocalHost().getHostAddress();

			// If the replica binds to the loopback address, clients will not be able to
			// connect to replicas.
			// To solve that issue, we bind to the address supplied in config/hosts.config
			// instead.
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
		this.serverSocketSSLTLS = (SSLServerSocket) 
					serverSocketFactory.createServerSocket(myPort, 100, InetAddress.getByName(myAddress));

		serverSocketSSLTLS.setEnabledCipherSuites(this.controller.getStaticConf().getEnabledCiphers());
		
		String [] ciphers = serverSocketFactory.getSupportedCipherSuites();
		for (int i = 0; i < ciphers.length; i++) {
			logger.trace("Supported Cipher: {} ", ciphers[i]);
		}
		

		serverSocketSSLTLS.setSoTimeout(30000);
		serverSocketSSLTLS.setEnableSessionCreation(true);
		serverSocketSSLTLS.setReuseAddress(true);
		serverSocketSSLTLS.setNeedClientAuth(true);
		serverSocketSSLTLS.setWantClientAuth(true);
		

		SecretKeyFactory fac = TOMUtil.getSecretFactory();
		PBEKeySpec spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
		selfPwd = fac.generateSecret(spec);
		
		// Try connecting if a member of the current view. Otherwise, wait until the
		// Join has been processed!
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

	// ******* EDUARDO BEGIN **************//
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

	private ServerConnectionSSLTLS getConnection(int remoteId){
		connectionsLock.lock();
		ServerConnectionSSLTLS ret = this.connections.get(remoteId);
		if (ret == null) {
			try {
				ret = new ServerConnectionSSLTLS(controller, null, remoteId, this.inQueue, this.replica);
			} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException
					| CertificateException e) {
				e.printStackTrace();
			}
			this.connections.put(remoteId, ret);
		}
		connectionsLock.unlock();
		return ret;
	}
	// ******* EDUARDO END **************//

	public final void send(int[] targets, SystemMessage sm, boolean useMAC) {
		ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
		try {
			new ObjectOutputStream(bOut).writeObject(sm);
		} catch (IOException ex) {
			logger.error("Failed to serialize message", ex);
		}

		byte[] data = bOut.toByteArray();

		for (int i : targets) {
			try {
				if (i == me) {
					sm.authenticated = true;
					inQueue.put(sm);
				} else {
					// logger.info("Going to send a message to replica: {}", i);
					// logger.info("Going to send a message to replica: {}, data:\n{} ", i, data);
					// ******* EDUARDO BEGIN **************//
					// connections[i].send(data);
					getConnection(i).send(data, useMAC);
					// ******* EDUARDO END **************//
				}
			} catch (InterruptedException ex) {
				logger.error("Interruption while inserting message into inqueue", ex);
			}
		}
	}

	public void shutdown() {

		logger.info("Shutting down replica sockets");

		doWork = false;

		// ******* EDUARDO BEGIN **************//
		int[] activeServers = controller.getCurrentViewAcceptors();

		for (int i = 0; i < activeServers.length; i++) {
			// if (connections[i] != null) {
			// connections[i].shutdown();
			// }
			if (me != activeServers[i]) {
				getConnection(activeServers[i]).shutdown();
			}
		}
	}

	// ******* EDUARDO BEGIN **************//
	public void joinViewReceived() {
		waitViewLock.lock();
		for (int i = 0; i < pendingConn.size(); i++) {
			PendingConnection pc = pendingConn.get(i);
			try {
				establishConnection(pc.s, pc.remoteId);
			} catch (Exception e) {
				logger.error("Failed to estabilish connection to " + pc.remoteId, e);
			}
		}

		pendingConn.clear();

		waitViewLock.unlock();
	}
	// ******* EDUARDO END **************//

	@Override
	public void run() {

		while (doWork) {
			try {
				// System.out.println("Waiting for connections.");
				SSLSocket newSocket = (SSLSocket) serverSocketSSLTLS.accept();
				ServersCommunicationLayerSSLTLS.setSSLSocketOptions(newSocket);

				int remoteId = new DataInputStream(newSocket.getInputStream()).readInt();

				// ******* EDUARDO BEGIN **************//
				if (!this.controller.isInCurrentView() && (this.controller.getStaticConf().getTTPId() != remoteId)) {
					waitViewLock.lock();
					pendingConn.add(new PendingConnection(newSocket, remoteId));
					waitViewLock.unlock();
				} else {
					logger.debug("Trying establish connection with Replica: {}", remoteId);
					establishConnection(newSocket, remoteId);
				}
				// ******* EDUARDO END **************//

			} catch (SocketTimeoutException ex) {
				logger.trace("Server socket timed out, retrying");
			} catch (javax.net.ssl.SSLHandshakeException sslex) {
				sslex.printStackTrace();
			} catch (IOException ex) {
				logger.error("Problem during thread execution", ex);
			}
		}

		try {
			serverSocketSSLTLS.close();
		} catch (IOException ex) {
			logger.error("Failed to close server socket", ex);
		}

		logger.info("ServerCommunicationLayer stopped.");
	}

	// Tulio SSL Socket. ## BEGIN
	private void establishConnection(SSLSocket newSocket, int remoteId) throws IOException {

		if ((this.controller.getStaticConf().getTTPId() == remoteId) || this.controller.isCurrentViewMember(remoteId)) {
			connectionsLock.lock();
			if (this.connections.get(remoteId) == null) { // This must never happen!!!
				// first time that this connection is being established
				// System.out.println("THIS DOES NOT HAPPEN....."+remoteId);
				try {
					this.connections.put(remoteId,
							new ServerConnectionSSLTLS(controller, newSocket, remoteId, inQueue, replica));
				} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException
						| NoSuchAlgorithmException | CertificateException e) {
					e.printStackTrace();
				}
			} else {
				// reconnection
				logger.debug("ReConnecting with replica: {}", remoteId);
				this.connections.get(remoteId).reconnect(newSocket);
			}
			connectionsLock.unlock();

		} else {
			logger.debug("Closing connection with replica: {}", remoteId);
			newSocket.close();
		}
	}

	public static void setSSLSocketOptions(SSLSocket socket) {
		try {
			socket.setTcpNoDelay(true);
		} catch (SocketException ex) {
			LoggerFactory.getLogger(ServersCommunicationLayer.class).error("Failed to set TCPNODELAY", ex);
		}
	}
	// Tulio SSL Socket ## END

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

	// ******* Tulio BEGIN: same as above with SSL.
	public class PendingConnection {
		public SSLSocket s;
		public int remoteId;

		public PendingConnection(SSLSocket s, int remoteId) {
			this.s = s;
			this.remoteId = remoteId;
		}
	}

	// ******* Tulio END **************//
	public SecretKey getSecretKey(int id) {
		if (id == controller.getStaticConf().getProcessId()) {
			return selfPwd;
		}
		else
			return connections.get(id).getSecretKey();
	}

}
