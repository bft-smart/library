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

import bftsmart.communication.SystemMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.util.TOMUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author alysson
 * Tulio A. Ribeiro.
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

	private final Logger logger = LoggerFactory.getLogger(this.getClass());


	private final ServerViewController controller;
	private final LinkedBlockingQueue<SystemMessage> inQueue;
	private final HashMap<Integer, ServerConnection> connections = new HashMap<>();
	private final int me;
	private boolean doWork = true;
	private final Lock connectionsLock = new ReentrantLock();
	private final ReentrantLock waitViewLock = new ReentrantLock();
	private final List<PendingConnection> pendingConn = new LinkedList<PendingConnection>();
	private final ServiceReplica replica;

	/**
	 * Tulio A. Ribeiro
	 * SSL / TLS.
	 */
	private static final String SECRET = "MySeCreT_2hMOygBwY";
	private final SecretKey selfPwd;
	private final SSLServerSocket serverSocketSSLTLS;

	public ServersCommunicationLayer(ServerViewController controller,
									 LinkedBlockingQueue<SystemMessage> inQueue,
									 ServiceReplica replica) throws Exception {

		this.controller = controller;
		this.inQueue = inQueue;
		this.me = controller.getStaticConf().getProcessId();
		this.replica = replica;
		String ssltlsProtocolVersion = controller.getStaticConf().getSSLTLSProtocolVersion();

		String myAddress;
		String confAddress = "";
		try {
			confAddress =
					controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId()).getAddress().getHostAddress();
		} catch (Exception e) {
			// Now look what went wrong ...
			logger.debug(" ####### Debugging at setting up the Communication layer ");
			logger.debug("my Id is " + controller.getStaticConf().getProcessId()
					+ " my remote Address is  " + controller.getStaticConf().getRemoteAddress(controller.getStaticConf().getProcessId()));
		}

		if (InetAddress.getLoopbackAddress().getHostAddress().equals(confAddress)) {
			myAddress = InetAddress.getLoopbackAddress().getHostAddress();
		} else if (controller.getStaticConf().getBindAddress().isEmpty()) {
			myAddress = InetAddress.getLocalHost().getHostAddress();
			//If the replica binds to the loopback address, clients will not be able to connect to replicas.
			//To solve that issue, we bind to the address supplied in config/hosts.config instead.
			if (InetAddress.getByName(myAddress).isLoopbackAddress() && !myAddress.equals(confAddress)) {
				myAddress = confAddress;
			}
		} else {
			myAddress = controller.getStaticConf().getBindAddress();
		}

		int myPort = controller.getStaticConf().getServerToServerPort(controller.getStaticConf().getProcessId());

		KeyStore ks;
		try (FileInputStream fis = new FileInputStream("config/keysSSL_TLS/" + controller.getStaticConf().getSSLTLSKeyStore())) {
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(fis, SECRET.toCharArray());
		}

		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
		kmf.init(ks, SECRET.toCharArray());

		TrustManagerFactory trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
		trustMgrFactory.init(ks);

		SSLContext context = SSLContext.getInstance(ssltlsProtocolVersion);
		context.init(kmf.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());

		SSLServerSocketFactory serverSocketFactory = context.getServerSocketFactory();
		this.serverSocketSSLTLS = (SSLServerSocket) serverSocketFactory.createServerSocket(myPort, 100,
				InetAddress.getByName(myAddress));

		serverSocketSSLTLS.setEnabledCipherSuites(this.controller.getStaticConf().getEnabledCiphers());

		String[] ciphers = serverSocketFactory.getSupportedCipherSuites();
		for (String cipher : ciphers) {
			logger.trace("Supported Cipher: {} ", cipher);
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
			for (int j : initialV) {
				if (j != me) {
					getConnection(j);
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
			List<Integer> toRemove = new LinkedList<>();
			while (it.hasNext()) {
				int rm = it.next();
				if (!this.controller.isCurrentViewMember(rm)) {
					toRemove.add(rm);
				}
			}
			for (Integer integer : toRemove) {
				this.connections.remove(integer).shutdown();
			}

			int[] newV = controller.getCurrentViewAcceptors();
			for (int j : newV) {
				if (j != me) {
					getConnection(j);
				}
			}
		} else {

			for (Integer integer : this.connections.keySet()) {
				this.connections.get(integer).shutdown();
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

		for (int activeServer : activeServers) {
			if (me != activeServer) {
				getConnection(activeServer).shutdown();
			}
		}
	}

	//******* EDUARDO BEGIN **************//
	public void joinViewReceived() {
		waitViewLock.lock();
		for (PendingConnection pc : pendingConn) {
			try {
				establishConnection(pc.s, pc.remoteId);
			} catch (Exception e) {
				logger.error("Failed to establish connection to " + pc.remoteId, e);
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
				logger.error("SSL handshake failed", sslex);
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
		StringBuilder str = new StringBuilder("inQueue=" + inQueue.toString());
		int[] activeServers = controller.getCurrentViewAcceptors();
		for (int activeServer : activeServers) {
			if (me != activeServer) {
				str.append(", connections[").append(activeServer).append("]: outQueue=").append(getConnection(activeServer).outQueue);
			}
		}
		return str.toString();
	}


	//******* EDUARDO BEGIN: List entry that stores pending connections,
	// as a server may accept connections only after learning the current view,
	// i.e., after receiving the response to the join*************//
	// This is for avoiding that the server accepts connectsion from everywhere
	public static class PendingConnection {

		public SSLSocket s;
		public int remoteId;

		public PendingConnection(SSLSocket s, int remoteId) {
			this.s = s;
			this.remoteId = remoteId;
		}
	}

	//******* EDUARDO END **************//
}
