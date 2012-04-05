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

import java.util.concurrent.TimeUnit;
import navigators.smart.communication.client.CommunicationSystemServerSide;
import navigators.smart.communication.client.CommunicationSystemServerSideFactory;
import navigators.smart.communication.client.RequestReceiver;
import navigators.smart.communication.server.ServersCommunicationLayer;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.tom.ServiceReplica;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Logger;

/**
 *
 * @author alysson
 */
public class ServerCommunicationSystem extends Thread {

    public final long MESSAGE_WAIT_TIME = 100;
    private LinkedBlockingQueue<SystemMessage> inQueue = null;//new LinkedBlockingQueue<SystemMessage>(IN_QUEUE_SIZE);
    protected MessageHandler messageHandler = new MessageHandler();
    private ServersCommunicationLayer serversConn;
    private CommunicationSystemServerSide clientsConn;
    private ServerViewManager manager;

    /**
     * Creates a new instance of ServerCommunicationSystem
     */
    public ServerCommunicationSystem(ServerViewManager manager, ServiceReplica replica) throws Exception {
        super("Server CS");

        this.manager = manager;

        inQueue = new LinkedBlockingQueue<SystemMessage>(manager.getStaticConf().getInQueueSize());

        //create a new conf, with updated port number for servers
        //TOMConfiguration serversConf = new TOMConfiguration(conf.getProcessId(),
        //      Configuration.getHomeDir(), "hosts.config");

        //serversConf.increasePortNumber();

        serversConn = new ServersCommunicationLayer(manager, inQueue, replica);

        //******* EDUARDO BEGIN **************//
       // if (manager.isInCurrentView() || manager.isInInitView()) {
            clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(manager);
       // }
        //******* EDUARDO END **************//
        //start();
    }

    //******* EDUARDO BEGIN **************//
    public void joinViewReceived() {
        serversConn.joinViewReceived();
    }

    public void updateServersConnections() {
        this.serversConn.updateConnections();
        if (clientsConn == null) {
            clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(manager);
        }

    }

    //******* EDUARDO END **************//
    public void setAcceptor(Acceptor acceptor) {
        messageHandler.setAcceptor(acceptor);
    }

    public void setTOMLayer(TOMLayer tomLayer) {
        messageHandler.setTOMLayer(tomLayer);
    }

    public void setRequestReceiver(RequestReceiver requestReceiver) {
        if (clientsConn == null) {
            clientsConn = CommunicationSystemServerSideFactory.getCommunicationSystemServerSide(manager);
        }
        clientsConn.setRequestReceiver(requestReceiver);
    }

    /**
     * Thread method responsible for receiving messages sent by other servers.
     */
    @Override
    public void run() {
        
        long count = 0;
        while (true) {
            try {
                if (count % 1000 == 0 && count > 0) {
                    Logger.println("(ServerCommunicationSystem.run) After " + count + " messages, inQueue size=" + inQueue.size());
                }

                SystemMessage sm = inQueue.poll(MESSAGE_WAIT_TIME, TimeUnit.MILLISECONDS);

                if (sm != null) {
                    Logger.println("<-------receiving---------- " + sm);
                    
                    messageHandler.processData(sm);
                    count++;
                } else {                
                    messageHandler.verifyPending();               
                }
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    /**
     * Send a message to target processes. If the message is an instance of 
     * TOMMessage, it is sent to the clients, otherwise it is set to the
     * servers.
     *
     * @param targets the target receivers of the message
     * @param sm the message to be sent
     */
    public void send(int[] targets, SystemMessage sm) {
        if (sm instanceof TOMMessage) {
            clientsConn.send(targets, (TOMMessage) sm, false);
        } else {
            Logger.println("--------sending----------> " + sm);
            serversConn.send(targets, sm);
        }
    }

    @Override
    public String toString() {
        return serversConn.toString();
    }
}
