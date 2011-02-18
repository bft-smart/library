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

package navigators.smart.communication;

import java.lang.reflect.InvocationTargetException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.CommunicationSystemServerSideFactory;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.communication.server.GlobalMessageVerifier;
import navigators.smart.communication.server.MessageVerifierFactory;
import navigators.smart.communication.server.PTPMessageVerifier;
import navigators.smart.communication.server.ServersCommunicationLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Configuration;
import navigators.smart.tom.util.TOMConfiguration;


/**
 *
 * @author alysson
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
 public class ServerCommunicationSystem extends Thread {

    private static final Logger log = Logger.getLogger(ServerCommunicationSystem.class.getName());

    public static int TOM_REQUEST_MSG = 1;
    public static int TOM_REPLY_MSG = 2;
    public static int PAXOS_MSG = 3;
    public static int RR_MSG = 4;
    public static int RT_MSG = 5;

    //public static int IN_QUEUE_SIZE = 200;

    private BlockingQueue<SystemMessage> inQueue = null;//new LinkedBlockingQueue<SystemMessage>(IN_QUEUE_SIZE);

    protected Map<SystemMessage.Type,MessageHandler> msgHandlers = new Hashtable<SystemMessage.Type, MessageHandler>();

    private ServersCommunicationLayer serversConn;
    private CommunicationSystemServerSide clientsConn;

    private GlobalMessageVerifier verifier;

    /**
     * Creates a new instance of ServerCommunicationSystem
     * @param conf The configuration object containing the conf
     * @throws Exception
     */
    public ServerCommunicationSystem(TOMConfiguration conf) throws Exception {
        super("Server CS");

        inQueue = new ArrayBlockingQueue<SystemMessage>(conf.getInQueueSize());
        
        //create a new conf, with updated port number for servers
        TOMConfiguration serversConf = new TOMConfiguration(conf.getProcessId(),
                Configuration.getHomeDir(), "hosts.config");

        serversConf.increasePortNumber();

        MessageVerifierFactory<PTPMessageVerifier> ptpFactory = null;
        if(conf.getUseMACs() == 1){
            ptpFactory = createVerifierFactory(conf.getPTPVerifierFactoryClassname());
            assert ptpFactory != null:"Failed to load HMAC Factory";
        }

        if(conf.isUseGlobalAuth()){
            verifier =  (GlobalMessageVerifier) createVerifierFactory(conf.getGlobalMessageVerifierFactoryClassName()).generateMessageVerifier();
            verifier.authenticateAndEstablishAuthKey();
            assert verifier != null : "Failed to load USIG Service";
        }
        
        serversConn = new ServersCommunicationLayer(serversConf,inQueue,msgHandlers,ptpFactory,verifier);

        clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(conf);

        //start();
    }
    
    @Override
    public void start(){
    	serversConn.start();
    	super.start();
    }

    @SuppressWarnings("unchecked")
    protected MessageVerifierFactory createVerifierFactory(String algorithm){
        Class<MessageVerifierFactory> serviceclass;
        try {
            serviceclass = (Class<MessageVerifierFactory>) Class.forName(algorithm);
            Object[] initargs = new Object[0];
            MessageVerifierFactory factory = (MessageVerifierFactory) serviceclass.getConstructors()[0].newInstance(initargs);
            return factory;
        } catch (InstantiationException ex) {
            Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public void addMessageHandler(SystemMessage.Type type, MessageHandler handler){
        assert(msgHandlers.size()<=Byte.MAX_VALUE);
        msgHandlers.put(type,handler);
    }

    public void setRequestReceiver(RequestReceiver requestReceiver) {
        clientsConn.setRequestReceiver(requestReceiver);
    }

    /**
     * Thread method resposible for receiving messages sent by other servers.
     */
    @Override
    public void run() {
        long count=0;
        while (true) {
            try {
                if (log.isLoggable(Level.FINE)) {
                    count++;
                    if (count % 1000 == 0) {
                        log.fine("(ServerCommunicationSystem.run) After " + count + " messages, inQueue size=" + inQueue.size());
                    }
                }
                SystemMessage msg = inQueue.take();
                //find a handler to process the msg
                msgHandlers.get(msg.type).processData(msg);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(ServerCommunicationSystem.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    /**
     * Used to send messages.
     *
     * @param targets the target receivers of the message
     * @param sm the message to be sent
     */
    public void send(int[] targets, SystemMessage sm) {
        if(sm.type.equals(SystemMessage.Type.TOM_MSG)) {
            //Logger.println("(ServerCommunicationSystem.send) C: "+sm);
            clientsConn.send(targets, (TOMMessage)sm);
        } else {
            //Logger.println("(ServerCommunicationSystem.send) S: "+sm);
            if(verifier != null){
                verifier.generateHash(sm);
            }
            serversConn.send(targets, sm);
        }
    }

//    /**
//     * Used to send messages.
//     *
//     * @param targets the target receivers of the message
//     * @param sm the message to be sent
//     * @param serializeClassHeaders to serialize java class headers or not,
//     * in case of sm not instanceof TOMMessage this does nothing.
//     */
//    public void send(int[] targets, SystemMessage sm, boolean serializeClassHeaders) {
//        if(sm instanceof TOMMessage) {
//            //Logger.println("(ServerCommunicationSystem.send) C: "+sm);
//            clientsConn.send(targets, (TOMMessage)sm, serializeClassHeaders);
//        } else {
//            //Logger.println("(ServerCommunicationSystem.send) S: "+sm);
//            serversConn.send(targets, sm);
//        }
//    }

    @Override
    public String toString() {
        return serversConn.toString();
    }
}

