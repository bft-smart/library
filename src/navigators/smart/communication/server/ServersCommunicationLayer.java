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

package navigators.smart.communication.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * 
 * @author alysson
 */
public class ServersCommunicationLayer extends Thread {
	
	private static final Logger log = Logger.getLogger(ServersCommunicationLayer.class.getCanonicalName());

	private TOMConfiguration conf;
	private BlockingQueue<SystemMessage> inQueue;
	private ServerConnection[] connections;
	private ServerSocketChannel serverSocket;
	private int me;
	private boolean doWork = true;
	private final Map<SystemMessage.Type, MessageHandler> msgHandlers;
	private MessageVerifierFactory<PTPMessageVerifier> verifierfactory;
	/** Holds the global verifier reference */
	private GlobalMessageVerifier<SystemMessage> globalverifier;
	private CountDownLatch latch;

	public ServersCommunicationLayer(TOMConfiguration conf,
			BlockingQueue<SystemMessage> inQueue,
			Map<SystemMessage.Type, MessageHandler> msgHandlers,
			MessageVerifierFactory<PTPMessageVerifier> verifierfactory,
			GlobalMessageVerifier<SystemMessage> globalverifier) throws IOException
			{
		super("ServersCommunicationLayer");
		this.conf = conf;
		this.inQueue = inQueue;
		this.me = conf.getProcessId();
		this.msgHandlers = msgHandlers;
		this.verifierfactory = verifierfactory;
		this.globalverifier = globalverifier;
		connections = new ServerConnection[conf.getN()];
		latch = new CountDownLatch(conf.getN() - 1); // create latch to wait for all connections

		serverSocket = ServerSocketChannel.open();
		serverSocket.socket().setSoTimeout(10000);
		serverSocket.socket().setReuseAddress(true);
		serverSocket.socket().bind(new InetSocketAddress(conf.getPort(conf.getProcessId())));
	}
	
	@Override
	public void start(){
		super.start();
		try {
			latch.await(); // wait for all connections on startup
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}

	public final void send(int[] targets, SystemMessage sm) {

		byte[] data = sm.getBytes();

		for (int i : targets) {
			if(log.isLoggable(Level.FINEST))
				log.finest("Sending "+sm+" to "+i);
			// br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Sending msg to replica "+i);
			try {
				if (i == me) {
					inQueue.put(sm);
				} else {
					connections[i].send(data);
				}
			} catch (InterruptedException ex) {
				ex.printStackTrace();
			}
		}
		// br.ufsc.das.tom.util.Logger.println("(ServersCommunicationLayer.send) Finished sending messages to replicas");
	}

	public void shutdown() {
		doWork = false;

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] != null) {
				connections[i].shutdown();
			}
		}
	}

	@Override
	public void run() {
		// connect to all lower ids than me, the rest will contact us
		for (int i = 0; i < me; i++) {
			PTPMessageVerifier verifier = null;
			// if (i != me) {
			if (verifierfactory != null) {
				verifier = verifierfactory.generateMessageVerifier();
			}
			connections[i] = new ServerConnection(conf, null, i, inQueue,
					msgHandlers, verifier, globalverifier);
			latch.countDown();
			// }
		}
		while (doWork) {
			try {
				SocketChannel newSocketChannel = serverSocket.accept();
				newSocketChannel.configureBlocking(true);
				ServersCommunicationLayer.setSocketOptions(newSocketChannel.socket());
				ByteBuffer buf = ByteBuffer.allocate(4);
				newSocketChannel.read(buf);
				buf.flip();
				int remoteId = buf.getInt();
				if (remoteId >= 0 && remoteId < connections.length) {
					if (connections[remoteId] == null) {
						// first time that this connection is being established
						PTPMessageVerifier verifier = null;
						if (verifierfactory != null) {
							verifier = verifierfactory
									.generateMessageVerifier();
						}
						connections[remoteId] = new ServerConnection(conf,
								newSocketChannel, remoteId, inQueue, msgHandlers,
								verifier, globalverifier);
						latch.countDown();
					} else {
						// reconnection
						connections[remoteId].reconnect(newSocketChannel);
					}
				} else {
					newSocketChannel.close();
				}
			} catch (SocketTimeoutException ex) {
				// timeout on the accept... do nothing
			} catch (IOException ex) {
				Logger.getLogger(ServersCommunicationLayer.class.getName())
						.log(Level.SEVERE, null, ex);
			}
		}

		try {
			serverSocket.close();
		} catch (IOException ex) {
			Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
					Level.SEVERE, null, ex);
		}

		Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
				Level.INFO, "Server communication layer stoped.");
	}

	public static void setSocketOptions(Socket socket) {
		try {
			socket.setTcpNoDelay(true);
		} catch (SocketException ex) {
			Logger.getLogger(ServersCommunicationLayer.class.getName()).log(
					Level.SEVERE, null, ex);
		}
	}

	@Override
	public String toString() {
		String str = "inQueue=" + inQueue.toString();

		for (int i = 0; i < connections.length; i++) {
			if (connections[i] != null) {
				str += ", connections[" + i + "]: outQueue="
						+ connections[i].outQueue;
			}
		}

		return str;
	}
}
