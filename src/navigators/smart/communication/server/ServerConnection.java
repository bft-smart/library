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
import java.io.ObjectInputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.reconfiguration.TTPMessage;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.core.messages.SystemMessage;

/**
 * This class represents a connection with other server.
 *
 * ServerConnections are created by ServerCommunicationLayer.
 *
 * @author alysson
 */
public class ServerConnection {

    private static final String PASSWORD = "newcs";
    private static final String MAC_ALGORITHM = "HmacMD5";
    private static final long POOL_TIME = 5000;
    //private static final int SEND_QUEUE_SIZE = 50;
    private ReconfigurationManager manager;
    private Socket socket;
    private DataOutputStream socketOutStream = null;
    private DataInputStream socketInStream = null;
    private int remoteId;
    private boolean useSenderThread;
    protected LinkedBlockingQueue<byte[]> outQueue;// = new LinkedBlockingQueue<byte[]>(SEND_QUEUE_SIZE);
    private LinkedBlockingQueue<SystemMessage> inQueue;
    private SecretKey authKey;
    private Mac macSend;
    private Mac macReceive;
    private int macSize;
    private Lock connectLock = new ReentrantLock();
    /** Only used when there is no sender Thread */
    private Lock sendLock;
    private boolean doWork = true;

    public ServerConnection(ReconfigurationManager manager, Socket socket, int remoteId,
            LinkedBlockingQueue<SystemMessage> inQueue, ServiceReplica replica) {

        this.manager = manager;

        this.socket = socket;

        this.remoteId = remoteId;

        this.inQueue = inQueue;


        this.outQueue = new LinkedBlockingQueue<byte[]>(this.manager.getStaticConf().getOutQueueSize());

        //******* EDUARDO BEGIN **************//
        //É para se conectar ao processo remoto ou apenas aguardar a conexao?
        if (isToConnect()) {
            //I have to connect to the remote server
            try {
                //System.out.println("**********");
                //System.out.println(remoteId);
                //System.out.println(this.manager.getStaticConf().getServerToServerPort(remoteId));
                //System.out.println(this.manager.getStaticConf().getHost(remoteId));
                this.socket = new Socket(this.manager.getStaticConf().getHost(remoteId),
                        this.manager.getStaticConf().getServerToServerPort(remoteId));
                ServersCommunicationLayer.setSocketOptions(this.socket);
                new DataOutputStream(this.socket.getOutputStream()).writeInt(this.manager.getStaticConf().getProcessId());

            } catch (UnknownHostException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        //else I have to wait a connection from the remote server
        //******* EDUARDO END **************//

        if (this.socket != null) {
            try {
                socketOutStream = new DataOutputStream(this.socket.getOutputStream());
                socketInStream = new DataInputStream(this.socket.getInputStream());



            } catch (IOException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

       //******* EDUARDO BEGIN **************//
        this.useSenderThread = this.manager.getStaticConf().isUseSenderThread();

        if (useSenderThread && (this.manager.getStaticConf().getTTPId() != remoteId)) {
            //Logger.getLogger(ServerConnection.class.getName()).log(Level.INFO, "Using sender thread.");
            new SenderThread().start();
        } else {
            sendLock = new ReentrantLock();
        }
        authenticateAndEstablishAuthKey();

        if (!this.manager.getStaticConf().isTheTTP()) {

            if (this.manager.getStaticConf().getTTPId() == remoteId) {
                //Uma thread "diferente" para as msgs recebidas da TTP
                new TTPReceiverThread(replica).start();
            } else {
                new ReceiverThread().start();
            }
        }
        //******* EDUARDO END **************//
    }

    /**
     * Stop message sending and reception.
     */
    public void shutdown() {

        //System.out.println("SHUTDOWN para "+remoteId);

        doWork = false;
        closeSocket();
    }

    /**
     * Used to send packets to the remote server.
     */
    public final void send(byte[] data) throws InterruptedException {


        if (useSenderThread) {
            //only enqueue messages if there queue is not full
            if (!outQueue.offer(data)) {
                navigators.smart.tom.util.Logger.println("(ServerConnection.send) out queue for " + remoteId + " full (message discarded).");
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
        int i = 0;
        do {
            if (socket != null && socketOutStream != null) {
                try {
                    socketOutStream.writeInt(messageData.length);
                    socketOutStream.write(messageData);
                    if (this.manager.getStaticConf().getUseMACs() == 1) {
                        socketOutStream.write(macSend.doFinal(messageData));
                    }
                    return;
                } catch (IOException ex) {
                    Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);

                    closeSocket();

                    waitAndConnect();
                }
            } else {
                waitAndConnect();
            }
            //br.ufsc.das.tom.util.Logger.println("(ServerConnection.sendBytes) iteration " + i);
            i++;
        } while (doWork);
    }

    //******* EDUARDO BEGIN **************//
    //retorna true caso o processo deve se conectar ao processo remoto, false no contrário
    private boolean isToConnect() {
        if (this.manager.getStaticConf().getTTPId() == remoteId) {
            //Deve aguardar o pedido de conexao vindo da TTP, nao tentar se conectar a ela
            return false;
        } else if (this.manager.getStaticConf().getTTPId() == this.manager.getStaticConf().getProcessId()) {
            //Se for a TTP deve se conectar ao processo remoto
            return true;
        }
        boolean ret = false;
        if (this.manager.isInCurrentView()) {
            boolean me = this.manager.isInLastJoinSet(this.manager.getStaticConf().getProcessId());
            boolean remote = this.manager.isInLastJoinSet(remoteId);

            //ou os dois são antigos no sistema (entraram em visoes anteriores)
            //ou os dois entraram na ultima reconfiguração
            if ((me && remote) || (!me && !remote)) {
                //neste caso, abre a conexao quem tem o ID maior
                if (this.manager.getStaticConf().getProcessId() > remoteId) {
                    ret = true;
                }
            //este processo é antigo e o outro entrou na ultima reconfiguração
            } else if (!me && remote) {
                ret = true;

            } //else if (me && !remote) { //este processo entrou na ultima reconfiguração e o outro é antigo
        //ret=false; //nem precisaria porque ret já é false
        //}
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
    protected void reconnect(Socket newSocket) {

        connectLock.lock();

        if (socket == null || !socket.isConnected()) {

            try {

                //******* EDUARDO BEGIN **************//
                if (isToConnect()) {

                    socket = new Socket(this.manager.getStaticConf().getHost(remoteId),
                            this.manager.getStaticConf().getServerToServerPort(remoteId));
                    ServersCommunicationLayer.setSocketOptions(socket);
                    new DataOutputStream(socket.getOutputStream()).writeInt(this.manager.getStaticConf().getProcessId());

                //******* EDUARDO END **************//
                } else {

                    socket = newSocket;
                }
            } catch (UnknownHostException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Error connecting", ex);
            } catch (IOException ex) {
            //Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Error connecting", ex);
            }

            if (socket != null) {

                try {
                    socketOutStream = new DataOutputStream(socket.getOutputStream());
                    socketInStream = new DataInputStream(socket.getInputStream());
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
                }
            }

        //authenticateAndEstablishAuthKey();
        }

        connectLock.unlock();
    }

    //TODO!
    public void authenticateAndEstablishAuthKey() {
        if (authKey != null) {
            return;
        }

        try {
            //if (conf.getProcessId() > remoteId) {
            // I asked for the connection, so I'm first on the auth protocol
            //DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            //} else {
            // I received a connection request, so I'm second on the auth protocol
            //DataInputStream dis = new DataInputStream(socket.getInputStream());
            //}

            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);

            macSend = Mac.getInstance(MAC_ALGORITHM);
            macSend.init(authKey);
            macReceive = Mac.getInstance(MAC_ALGORITHM);
            macReceive.init(authKey);
            macSize = macSend.getMacLength();
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void closeSocket() {
        if (socket != null) {
            try {
                socketOutStream.flush();
                socket.close();
            } catch (IOException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
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

    private final SystemMessage bytesToMessage(byte[] data) {
        try {
            ObjectInputStream obIn = new ObjectInputStream(new ByteArrayInputStream(data));
            return (SystemMessage) obIn.readObject();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
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
                    sendBytes(data);
                }
            }

            Logger.getLogger(ServerConnection.class.getName()).log(Level.INFO, "Sender for " + remoteId + " stoped!");
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
            byte[] receivedMac = null;
            try {
                receivedMac = new byte[Mac.getInstance(MAC_ALGORITHM).getMacLength()];
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
            }

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
                        boolean result = true;
                        if (manager.getStaticConf().getUseMACs() == 1) {
                            read = 0;
                            do {
                                read += socketInStream.read(receivedMac, read, macSize - read);
                            } while (read < macSize);

                            result = Arrays.equals(macReceive.doFinal(data), receivedMac);
                        }

                        if (result) {
                            SystemMessage sm = (SystemMessage) (new ObjectInputStream(new ByteArrayInputStream(data)).readObject());

                            if (sm.getSender() == remoteId) {
                                //System.out.println("Mensagem recebia de: "+remoteId);
                                if (!inQueue.offer(sm)) {
                                    navigators.smart.tom.util.Logger.println("(ReceiverThread.run) in queue full (message from " + remoteId + " discarded).");
                                    System.out.println("(ReceiverThread.run) in queue full (message from " + remoteId + " discarded).");
                                }
                            }
                        } else {
                            //TODO: violation of authentication... we should do something
                            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "WARNING: Violation of authentication in message received from " + remoteId);
                        }
                    /*
                    } else {
                    //TODO: invalid MAC... we should do something
                    Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "WARNING: Invalid MAC");
                    }
                     */
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Should never happen,", ex);
                    } catch (IOException ex) {

                        if (doWork) {
                            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Closing socket and reconnecting", ex);

                            closeSocket();

                            waitAndConnect();
                        }
                    }
                } else {
                    waitAndConnect();
                }
            }

            Logger.getLogger(ServerConnection.class.getName()).log(Level.INFO, "Receiver for " + remoteId + " stoped!");
        }
    }

    //******* EDUARDO BEGIN: thread especial para receber mensagens indicando a entrada no sistema, vindas da da TTP **************//

    //Simplesmente entrega a mensagens para a replica, indicando a sua entrada no sistema

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
            byte[] receivedMac = null;
            try {
                receivedMac = new byte[Mac.getInstance(MAC_ALGORITHM).getMacLength()];
            } catch (NoSuchAlgorithmException ex) {
                Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
            }

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
                        boolean result = true;
                        if (manager.getStaticConf().getUseMACs() == 1) {
                            read = 0;
                            do {
                                read += socketInStream.read(receivedMac, read, macSize - read);
                            } while (read < macSize);

                            result = Arrays.equals(macReceive.doFinal(data), receivedMac);
                        }

                        if (result) {
                            SystemMessage sm = (SystemMessage) (new ObjectInputStream(new ByteArrayInputStream(data)).readObject());

                            if (sm.getSender() == remoteId) {
                                //System.out.println("Mensagem recebia de: "+remoteId);
                                /*if (!inQueue.offer(sm)) {
                                navigators.smart.tom.util.Logger.println("(ReceiverThread.run) in queue full (message from " + remoteId + " discarded).");
                                System.out.println("(ReceiverThread.run) in queue full (message from " + remoteId + " discarded).");
                                }*/
                                this.replica.joinMsgReceived((TTPMessage) sm);
                            }
                        } else {
                            //TODO: violation of authentication... we should do something
                            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "WARNING: Violation of authentication in message received from " + remoteId);
                        }
                    /*
                    } else {
                    //TODO: invalid MAC... we should do something
                    Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "WARNING: Invalid MAC");
                    }
                     */
                    } catch (ClassNotFoundException ex) {
                        Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Should never happen,", ex);
                    } catch (IOException ex) {

                        if (doWork) {
                            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, "Closing socket and reconnecting", ex);

                            closeSocket();

                            waitAndConnect();
                        }
                    }
                } else {
                    waitAndConnect();
                }
            }

            Logger.getLogger(ServerConnection.class.getName()).log(Level.INFO, "Receiver for " + remoteId + " stoped!");
        }
    }
        //******* EDUARDO END **************//
}
