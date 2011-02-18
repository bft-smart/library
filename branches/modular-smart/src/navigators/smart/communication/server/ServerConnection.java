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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;


/**
 * This class represents a connection with other server.
 *
 * ServerConnections are created by ServerCommunicationLayer.
 *
 * @author alysson
 */
public class ServerConnection {

    private static final Logger log = Logger.getLogger(ServerConnection.class.getName());

    
    
    private static final long POOL_TIME = 10000;
    //private static final int SEND_QUEUE_SIZE = 50;
    private TOMConfiguration conf;
    private Socket socket;
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

    private PTPMessageVerifier ptpverifier;
    
    private GlobalMessageVerifier<SystemMessage> globalverifier;

    @SuppressWarnings("unchecked")
	private final Map<SystemMessage.Type,MessageHandler> msgHandlers;

	@SuppressWarnings("unchecked")
	public ServerConnection(TOMConfiguration conf, Socket socket, int remoteId,
			LinkedBlockingQueue<SystemMessage> inQueue,
			Map<SystemMessage.Type, MessageHandler> msgHandlers,
			PTPMessageVerifier ptpverifier,
			GlobalMessageVerifier<SystemMessage> verifier, CountDownLatch latch) {
        this.msgHandlers = msgHandlers;
        this.conf = conf;
        this.socket = socket;
        this.remoteId = remoteId;
        this.inQueue = inQueue;
        this.outQueue = new LinkedBlockingQueue<byte[]>(this.conf.getOutQueueSize());
        this.ptpverifier = ptpverifier;
        this.globalverifier = verifier;

        if (conf.getProcessId() > remoteId) {
            //higher process ids connect to lower ones
            try {
                this.socket = new Socket(conf.getHost(remoteId), conf.getPort(remoteId));
                ServersCommunicationLayer.setSocketOptions(this.socket);
                new DataOutputStream(this.socket.getOutputStream()).writeInt(conf.getProcessId());
                if(conf.getUseMACs() == 1){
                    ptpverifier.authenticateAndEstablishAuthKey();
                }
            } catch (UnknownHostException ex) {
                log.log(Level.SEVERE, "cannot open listening port", ex);
            } catch (IOException ex) {
                log.log(Level.SEVERE, "cannot open listening port", ex);
            }
        }
        //else I have to wait a connection from the remote server

        if (this.socket != null) {
            try {
            	latch.countDown(); // got connection, inform comlayer
                socketOutStream = new DataOutputStream(this.socket.getOutputStream());
                socketInStream = new DataInputStream(this.socket.getInputStream());
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }
        }

        this.useSenderThread = conf.isUseSenderThread();

        if (useSenderThread) {
            //log.log(Level.INFO, "Using sender thread.");
            new SenderThread().start();
        } else {
            sendLock = new ReentrantLock();
        }

        new ReceiverThread().start();
    }

    /**
     * Stop message sending and reception.
     */
    public void shutdown() {
        doWork = false;
        closeSocket();
    }

    /**
     * Used to send packets to the remote server.
     * @param data The data to send
     * @throws InterruptedException
     */
    public final void send(byte[] data) throws InterruptedException {
        if (useSenderThread) {
            //only enqueue messages if there queue is not full
            if (!outQueue.offer(data)) {
                if(log.isLoggable(Level.FINE)){
                    log.fine("out queue for "+remoteId+" full (message discarded).");
                }
            }
        } else {
            sendLock.lock();
            sendBytes(data);
            sendLock.unlock();
        }
    }

    /**
     * try to send a message through the socket
     * if some problem is detected, a reconnection is done
     */
    private final void sendBytes(byte[] messageData) {
        int i=0;
        do {            
            if (socket != null && socketOutStream != null) {
                try {
                    socketOutStream.writeInt(messageData.length);
                    socketOutStream.write(messageData);
                    if (conf.getUseMACs()==1) {
                        socketOutStream.write(ptpverifier.generateHash(messageData));
                    }
                    return;
                } catch (IOException ex) {
                    log.log(Level.SEVERE, null, ex);

                    closeSocket();

                    waitAndConnect();
                }
            } else {
                waitAndConnect();
            }
            i++;
        } while (true);
    }

    /**
     * (Re-)establish connection between peers.
     *
     * @param newSocket socket created when this server accepted the connection
     * (only used if processId is less than remoteId)
     */
    protected void reconnect(Socket newSocket) {
        connectLock.lock();

        if (socket == null || !socket.isConnected()) {
            try {
                if (conf.getProcessId() > remoteId) {
                    socket = new Socket(conf.getHost(remoteId), conf.getPort(remoteId));
                    ServersCommunicationLayer.setSocketOptions(socket);
                    new DataOutputStream(socket.getOutputStream()).writeInt(conf.getProcessId());
                } else {
                    socket = newSocket;
                }
            } catch (UnknownHostException ex) {
                log.log(Level.SEVERE, "Error connecting", ex);
            } catch (IOException ex) {
//                log.log(Level.SEVERE, "Error connecting", ex); ignore and retry
            }

            if (socket != null) {
                if(log.isLoggable(Level.INFO)){
                  log.fine("Reconnected to "+remoteId);
                }
                try {
                    socketOutStream = new DataOutputStream(socket.getOutputStream());
                    socketInStream = new DataInputStream(socket.getInputStream());
                } catch (IOException ex) {
                    log.log(Level.SEVERE, null, ex);
                }
            }
            if(conf.getUseMACs()==1){
                ptpverifier.authenticateAndEstablishAuthKey();
            }
        }

        connectLock.unlock();
    }

   

    private void closeSocket() {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ex) {
                log.log(Level.SEVERE, null, ex);
            }

            socket = null;
            socketOutStream = null;
            socketInStream = null;
        }
    }

    private void waitAndConnect() {
        if (doWork) {
            try {
                Thread.sleep(POOL_TIME);
            } catch (InterruptedException ie) {
            }

            reconnect(null);
        }
    }

    /**
     * Thread used to send packets to the remote server.
     */
    private class SenderThread extends Thread {

        public SenderThread() {
            super("Sender for "+remoteId);
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
                    sendBytes(data);
                }
            }

            log.log(Level.INFO, "Sender for " + remoteId + " stopped!");
        }
    }

    /**
     * Thread used to receive packets from the remote server.
     */
    protected class ReceiverThread extends Thread {

        private byte[] receivedHash;    //array to store the received hashes

        public ReceiverThread() {
            super("Receiver for "+remoteId);
            if(ptpverifier != null){
                receivedHash = new byte[ptpverifier.getHashSize()];
            }
        }

        @SuppressWarnings("unchecked")
		@Override
        public void run() {

            while (doWork) {
                if (socket != null && socketInStream != null) {
                    try {
                        //read data length
                        int dataLength = socketInStream.readInt();

                        byte[] data = new byte[dataLength];

                        //read data
                        int read = 0;
                        do {
                            read += socketInStream.read(data, read, dataLength - read);
                        } while (read < dataLength);

                        //read mac
                        Object verificationresult = null;
                        if (conf.getUseMACs()==1){
                            read = 0;
                            do {
                                read += socketInStream.read(receivedHash, read, receivedHash.length - read);
                            } while (read < receivedHash.length);

                            verificationresult = ptpverifier.verifyHash(data,receivedHash);
                        }
                        if(conf.isUseGlobalAuth()){
                        	verificationresult = globalverifier.verifyHash(data);
                        }
                        if (verificationresult != null || conf.getUseMACs() == 0 && !conf.isUseGlobalAuth()) {
                        	DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
                        	SystemMessage.Type type = SystemMessage.Type.getByByte(data[0]);
                        	SystemMessage sm = msgHandlers.get(type).deserialise(type,in, verificationresult);
                        	
                        	if (sm.getSender() == remoteId) {
                        		if(!inQueue.offer(sm)) 
                        			navigators.smart.tom.util.Logger.println("(ReceiverThread.run) in queue full (message from "+remoteId+" discarded).");
                        	}
                        } else {
                        	//TODO: violation of authentication... we should do something
                        	log.log(Level.SEVERE, "WARNING: Violation of authentication in message received from "+remoteId);
                        }

                        /*
                        } else {
                            //TODO: invalid MAC... we should do something
                            log.log(Level.SEVERE, "WARNING: Invalid MAC");
                        }
                        */
                    } catch (ClassNotFoundException ex) {
                        log.log(Level.SEVERE, "Should never happen,", ex);
                    } catch (SocketException e){
                    	 log.log(Level.FINE, "Socket reset. Reconnecting...");

                         closeSocket();

                         waitAndConnect();
                    } catch (IOException ex) {
                        log.log(Level.SEVERE,  "IO Error. Reconnecting...", ex);

                        closeSocket();

                        waitAndConnect();
                    }
                } else {
                    waitAndConnect();
                }
            }

            log.log(Level.INFO, "Receiver for " + remoteId + " stopped!");
        }
    }
}