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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import bftsmart.communication.SystemMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.VMMessage;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.util.TOMUtil;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents a connection with other server.
 *
 * ServerConnections are created by ServerCommunicationLayer.
 *
 * @author alysson
 */
public class ServerConnection {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());


    private static final long POOL_TIME = 5000;
    private ServerViewController controller;
    private SSLSocket socket;
    private DataOutputStream socketOutStream = null;
    private DataInputStream socketInStream = null;
    private int remoteId;
    private boolean useSenderThread;
    protected LinkedBlockingQueue<byte[]> outQueue;// = new LinkedBlockingQueue<byte[]>(SEND_QUEUE_SIZE);
    private LinkedBlockingQueue<SystemMessage> inQueue;
    
    private Lock connectLock = new ReentrantLock();
    /** Only used when there is no sender Thread */
    private Lock sendLock;
    private boolean doWork = true;
    
    private SecretKey secretKey = null;

    /**
     * Tulio A. Ribeiro
     * TLS vars. 
     */
    private KeyManagerFactory kmf;
	private KeyStore ks = null;
	private FileInputStream fis = null;
	private TrustManagerFactory trustMgrFactory;
	private SSLContext context;
	private SSLSocketFactory socketFactory;
	private static final String SECRET = "MySeCreT_2hMOygBwY";
    
    public ServerConnection(ServerViewController controller, 
    		SSLSocket socket, int remoteId,
            LinkedBlockingQueue<SystemMessage> inQueue, 
            ServiceReplica replica) {

        this.controller = controller;

        this.socket = socket;

        this.remoteId = remoteId;

        this.inQueue = inQueue;

        this.outQueue = new LinkedBlockingQueue<byte[]>(this.controller.getStaticConf().getOutQueueSize());

        // Connect to the remote process or just wait for the connection?
     		if (isToConnect()) {
     			ssltlsCreateConnection();
     		}
     		
     		if (this.socket != null) {
    			try {
    				socketOutStream = new DataOutputStream(this.socket.getOutputStream());
    				socketInStream = new DataInputStream(this.socket.getInputStream());
    			} catch (IOException ex) {
    				logger.error("Error creating connection to " + remoteId, ex);
    			}
    		}
               
       //******* EDUARDO BEGIN **************//
        this.useSenderThread = this.controller.getStaticConf().isUseSenderThread();

        if (useSenderThread && (this.controller.getStaticConf().getTTPId() != remoteId)) {
            new SenderThread().start();
        } else {
            sendLock = new ReentrantLock();
        }
        
        if (!this.controller.getStaticConf().isTheTTP()) {
            if (this.controller.getStaticConf().getTTPId() == remoteId) {
                //Uma thread "diferente" para as msgs recebidas da TTP
                new TTPReceiverThread(replica).start();
            } else {
                new ReceiverThread().start();
            }
        }
        //******* EDUARDO END **************//
    }
/**
 * Tulio A. Ribeiro.
 * @return SecretKey
 */
    public SecretKey getSecretKey() {
		if (secretKey != null)
			return secretKey;
		else {
			SecretKeyFactory fac;
			PBEKeySpec spec;
			try {
				fac = TOMUtil.getSecretFactory();
				spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
				secretKey = fac.generateSecret(spec);
			} catch (NoSuchAlgorithmException |InvalidKeySpecException e) {
				logger.error("Algorithm error.",e);
			}
		}
		return secretKey;
	}
    
    /**
     * Stop message sending and reception.
     */
    public void shutdown() {
        logger.debug("SHUTDOWN for "+remoteId);
        
        doWork = false;
        closeSocket();
    }

    /**
     * Used to send packets to the remote server.
     */
    public final void send(byte[] data) throws InterruptedException {
    	if (useSenderThread) {
			// only enqueue messages if there queue is not full
			if (!outQueue.offer(data)) {
				logger.debug("Out queue for " + remoteId + " full (message discarded).");
			}
		} else {
			sendLock.lock();
			sendBytes(data);
			sendLock.unlock();
		}
    }

    /**
	 * try to send a message through the socket if some problem is detected, a
	 * reconnection is done
	 */
	private final void sendBytes(byte[] messageData) {
		boolean abort = false;
		do {
			if (abort)
				return; // if there is a need to reconnect, abort this method
			if (socket != null && socketOutStream != null) {
				try {
					// do an extra copy of the data to be sent, but on a single out stream write
					byte[] data = new byte[5 + messageData.length];// without MAC
					int value = messageData.length;

					System.arraycopy(new byte[] { (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8),
							(byte) value }, 0, data, 0, 4);
					System.arraycopy(messageData, 0, data, 4, messageData.length);
					System.arraycopy(new byte[] { (byte) 0 }, 0, data, 4 + messageData.length, 1);

					socketOutStream.write(data);

					return;
				} catch (IOException ex) {
					closeSocket();
					waitAndConnect();
					abort = true;
				}
			} else {
				waitAndConnect();
				abort = true;
			}
		} while (doWork);
	}

    //******* EDUARDO BEGIN **************//
    //return true of a process shall connect to the remote process, false otherwise
    private boolean isToConnect() {
        if (this.controller.getStaticConf().getTTPId() == remoteId) {
            //Need to wait for the connection request from the TTP, do not tray to connect to it
            return false;
        } else if (this.controller.getStaticConf().getTTPId() == this.controller.getStaticConf().getProcessId()) {
            //If this is a TTP, one must connect to the remote process
            return true;
        }
        boolean ret = false;
        if (this.controller.isInCurrentView()) {
            
             //in this case, the node with higher ID starts the connection
             if (this.controller.getStaticConf().getProcessId() > remoteId) {
                 ret = true;
             }
                
            /** JCS: I commented the code below to fix a bug, but I am not sure
             whether its completely useless or not. The 'if' above was taken
             from that same code (its the only part I understand why is necessary)
             I keep the code commented just to be on the safe side*/
            
            /**
            
            boolean me = this.controller.isInLastJoinSet(this.controller.getStaticConf().getProcessId());
            boolean remote = this.controller.isInLastJoinSet(remoteId);

            //either both endpoints are old in the system (entered the system in a previous view),
            //or both entered during the last reconfiguration
            if ((me && remote) || (!me && !remote)) {
                //in this case, the node with higher ID starts the connection
                if (this.controller.getStaticConf().getProcessId() > remoteId) {
                    ret = true;
                }
            //this process is the older one, and the other one entered in the last reconfiguration
            } else if (!me && remote) {
                ret = true;

            } //else if (me && !remote) { //this process entered in the last reconfig and the other one is old
                //ret=false; //not necessary, as ret already is false
            //}
              
            */
        }
        return ret;
    }
    //******* EDUARDO END **************//


    /**
     * (Re-)establish connection between peers.
     *
     * @param newSocket socket created when this server accepted the connection
     * (only used if processId is less than remoteId)
     */
    protected void reconnect(SSLSocket newSocket) {

		connectLock.lock();

		if (socket == null || !socket.isConnected()) {

			if (isToConnect()) {
				ssltlsCreateConnection();
			} else {
				socket = newSocket;
			}

			if (socket != null) {
				try {
					socketOutStream = new DataOutputStream(socket.getOutputStream());
					socketInStream = new DataInputStream(socket.getInputStream());

					// authKey = null;
					// authenticateAndEstablishAuthKey();
				} catch (IOException ex) {
					logger.error("Failed to authenticate to replica", ex);
				}
			}
		}

		connectLock.unlock();
	}

  
    private void closeSocket() {
        
        connectLock.lock();
        
        if (socket != null) {
            try {
                socketOutStream.flush();
                socket.close();
            } catch (IOException ex) {
                logger.debug("Error closing socket to "+remoteId);
            } catch (NullPointerException npe) {
            	logger.debug("Socket already closed");
            }

            socket = null;
            socketOutStream = null;
            socketInStream = null;
        }
        
        connectLock.unlock();
    }

    private void waitAndConnect() {
        if (doWork) {
            try {
                Thread.sleep(POOL_TIME);
            } catch (InterruptedException ie) {
            }

            outQueue.clear();
            reconnect(null);
        }
    }

    /**
     * Thread used to send packets to the remote server.
     */
    private class SenderThread extends Thread {

        public SenderThread() {
            super("Sender for " + remoteId);
        }

        @Override
        public void run() {
            byte[] data = null;

            while (doWork) {
                //get a message to be sent
                try {
                    data = outQueue.poll(POOL_TIME, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                }

                if (data != null) {
					logger.trace("Sending data to, RemoteId:{}", remoteId);
					sendBytes(data);
				}
            }

            logger.debug("Sender for " + remoteId + " stopped!");
        }
    }

    /**
     * Thread used to receive packets from the remote server.
     */
    protected class ReceiverThread extends Thread {

        public ReceiverThread() {
            super("Receiver for " + remoteId);
        }

        @Override
        public void run() {
          
        	while (doWork) {
				if (socket != null && socketInStream != null) {

					try {
						// read data length
						int dataLength = socketInStream.readInt();
						byte[] data = new byte[dataLength];

						// read data
						int read = 0;
						do {
							read += socketInStream.read(data, read, dataLength - read);
						} while (read < dataLength);

						byte hasMAC = socketInStream.readByte();

						logger.trace("Read: {}, HasMAC: {}", read, hasMAC);

						SystemMessage sm = (SystemMessage) (new ObjectInputStream(new ByteArrayInputStream(data))
								.readObject());

						//The verification it is done for the SSL/TLS protocol.
						sm.authenticated = true;

						if (sm.getSender() == remoteId) {
							if (!inQueue.offer(sm)) {
								logger.warn("Inqueue full (message from " + remoteId + " discarded).");
							}/* else {
								logger.trace("Message: {} queued, remoteId: {}", sm.toString(), sm.getSender());
							}*/
						}
					} catch (ClassNotFoundException ex) {
						logger.info("Invalid message received. Ignoring!");
					} catch (IOException ex) {
						if (doWork) {
							logger.debug("Closing socket and reconnecting");
							closeSocket();
							waitAndConnect();
						}
					}
				} else {
					waitAndConnect();
				}
			}
        }
    }

    //******* EDUARDO BEGIN: special thread for receiving messages indicating the entrance into the system, coming from the TTP **************//
    // Simly pass the messages to the replica, indicating its entry into the system
    //TODO: Ask eduardo why a new thread is needed!!! 
    //TODO2: Remove all duplicated code

    /**
     * Thread used to receive packets from the remote server.
     */
    protected class TTPReceiverThread extends Thread {

        private ServiceReplica replica;

        public TTPReceiverThread(ServiceReplica replica) {
            super("TTPReceiver for " + remoteId);
            this.replica = replica;
        }

        @Override
		public void run() {

			while (doWork) {
				if (socket != null && socketInStream != null) {
					try {
						// read data length
						int dataLength = socketInStream.readInt();

						byte[] data = new byte[dataLength];

						// read data
						int read = 0;
						do {
							read += socketInStream.read(data, read, dataLength - read);
						} while (read < dataLength);

						SystemMessage sm = (SystemMessage) (new ObjectInputStream(new ByteArrayInputStream(data))
								.readObject());

						if (sm.getSender() == remoteId) {
							this.replica.joinMsgReceived((VMMessage) sm);
						}

					} catch (ClassNotFoundException ex) {
						logger.error("Failed to deserialize message", ex);
					} catch (IOException ex) {
						// ex.printStackTrace();
						if (doWork) {
							closeSocket();
							waitAndConnect();
						}
					}
				} else {
					waitAndConnect();
				}
			}
		}
    }
        //******* EDUARDO END **************//
    
    
    /**
	 * Deal with the creation of SSL/TLS connection.
	 *  Author: Tulio A. Ribeiro
	 *  
	 * @throws KeyStoreException
	 * @throws IOException
	 * @throws CertificateException
	 * @throws NoSuchAlgorithmException
	 * @throws UnrecoverableKeyException
	 * @throws KeyManagementException
	 * @throws InvalidKeySpecException
	 */

	public void ssltlsCreateConnection() {

		SecretKeyFactory fac;
		PBEKeySpec spec;
		try {
			fac = TOMUtil.getSecretFactory();
			spec = TOMUtil.generateKeySpec(SECRET.toCharArray());
			secretKey = fac.generateSecret(spec);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			logger.error("Algorithm error.", e);
		}

		String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
		try {
			fis = new FileInputStream("config/keysSSL_TLS/" + this.controller.getStaticConf().getSSLTLSKeyStore());
			ks = KeyStore.getInstance(KeyStore.getDefaultType());
			ks.load(fis, SECRET.toCharArray());
		} catch (FileNotFoundException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
			logger.error("SSL connection error.",e);
		} catch (IOException e) {
			logger.error("SSL connection error.",e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					logger.error("IO error.",e);
				}
			}
		}
		try {
			kmf = KeyManagerFactory.getInstance(algorithm);
			kmf.init(ks, SECRET.toCharArray());

			trustMgrFactory = TrustManagerFactory.getInstance(algorithm);
			trustMgrFactory.init(ks);
			context = SSLContext.getInstance(this.controller.getStaticConf().getSSLTLSProtocolVersion());
			context.init(kmf.getKeyManagers(), trustMgrFactory.getTrustManagers(), new SecureRandom());
			socketFactory = context.getSocketFactory();

		} catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException | KeyManagementException e) {
			logger.error("SSL connection error.",e);
		}
		// Create the connection.
		try {
			this.socket = (SSLSocket) socketFactory.createSocket(this.controller.getStaticConf().getHost(remoteId),
					this.controller.getStaticConf().getServerToServerPort(remoteId));
			this.socket.setKeepAlive(true);
			this.socket.setTcpNoDelay(true);
			this.socket.setEnabledCipherSuites(this.controller.getStaticConf().getEnabledCiphers());

			this.socket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
				@Override
				public void handshakeCompleted(HandshakeCompletedEvent event) {
					logger.info("SSL/TLS handshake complete!, Id:{}" + "  ## CipherSuite: {}.", remoteId,
							event.getCipherSuite());
				}
			});

			this.socket.startHandshake();

			ServersCommunicationLayer.setSSLSocketOptions(this.socket);
			new DataOutputStream(this.socket.getOutputStream())
					.writeInt(this.controller.getStaticConf().getProcessId());

		} catch (SocketException e) {
			logger.error("Connection refused (SocketException)");
			// e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
