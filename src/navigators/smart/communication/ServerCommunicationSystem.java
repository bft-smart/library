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

import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.CommunicationSystemServerSideFactory;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.communication.server.ServersCommunicationLayer;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Configuration;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMConfiguration;


/**
 *
 * @author alysson
 */
public class ServerCommunicationSystem extends Thread {

    public static int TOM_REQUEST_MSG = 1;
    public static int TOM_REPLY_MSG = 2;
    public static int PAXOS_MSG = 3;
    public static int RR_MSG = 4;
    public static int RT_MSG = 5;

    //public static int IN_QUEUE_SIZE = 200;

    private LinkedBlockingQueue<SystemMessage> inQueue = null;//new LinkedBlockingQueue<SystemMessage>(IN_QUEUE_SIZE);

    protected MessageHandler messageHandler = new MessageHandler();

    private ServersCommunicationLayer serversConn;
    private CommunicationSystemServerSide clientsConn;

    /**
     * Creates a new instance of ServerCommunicationSystem
     */
    public ServerCommunicationSystem(TOMConfiguration conf) throws Exception {
        super("Server CS");

        inQueue = new LinkedBlockingQueue<SystemMessage>(conf.getInQueueSize());
        
        //create a new conf, with updated port number for servers
        TOMConfiguration serversConf = new TOMConfiguration(conf.getProcessId(),
                Configuration.getHomeDir(), "hosts.config");

        serversConf.increasePortNumber();

        serversConn = new ServersCommunicationLayer(serversConf,inQueue);

        clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(conf);

        //start();
    }

    public void setProposer(Proposer proposer) {
        messageHandler.setProposer(proposer);
    }

    public void setAcceptor(Acceptor acceptor) {
        messageHandler.setAcceptor(acceptor);
    }

    public void setTOMLayer(TOMLayer tomLayer) {
        messageHandler.setTOMLayer(tomLayer);
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
                count++;
                if (count % 1000==0)
                    Logger.println("(ServerCommunicationSystem.run) After "+count+" messages, inQueue size="+inQueue.size());
                messageHandler.processData(inQueue.take());
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
        if(sm instanceof TOMMessage) {
            //Logger.println("(ServerCommunicationSystem.send) C: "+sm);
            clientsConn.send(targets, (TOMMessage)sm, false);
        } else {
            //Logger.println("(ServerCommunicationSystem.send) S: "+sm);
            serversConn.send(targets, sm);
        }
    }

    /**
     * Used to send messages.
     *
     * @param targets the target receivers of the message
     * @param sm the message to be sent
     * @param serializeClassHeaders to serialize java class headers or not,
     * in case of sm not instanceof TOMMessage this does nothing.
     */
    public void send(int[] targets, SystemMessage sm, boolean serializeClassHeaders) {
        if(sm instanceof TOMMessage) {
            //Logger.println("(ServerCommunicationSystem.send) C: "+sm);
            clientsConn.send(targets, (TOMMessage)sm, serializeClassHeaders);
        } else {
            //Logger.println("(ServerCommunicationSystem.send) S: "+sm);
            serversConn.send(targets, sm);
        }
    }

    @Override
    public String toString() {
        return serversConn.toString();
    }
}

