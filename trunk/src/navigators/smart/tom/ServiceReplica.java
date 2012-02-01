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
package navigators.smart.tom;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.reconfiguration.Reconfiguration;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.reconfiguration.ReconfigureReply;
import navigators.smart.reconfiguration.TTPMessage;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.messages.TOMMessageType;
import navigators.smart.tom.server.BatchExecutable;
import navigators.smart.tom.server.Executable;
import navigators.smart.tom.server.Recoverable;
import navigators.smart.tom.server.SingleExecutable;
import navigators.smart.tom.util.ShutdownHookThread;
import navigators.smart.tom.util.TOMUtil;

/**
 * This class implements a TOMReceiver, and also a replica for the server side of the application.
 * It receives requests from the clients, runs a TOM layer, and sends a reply back to the client
 * Applications must create a class that extends this one, and implement the executeOrdered method
 *
 */
public class ServiceReplica implements TOMReceiver {

    class MessageContextPair {

        TOMMessage message;
        MessageContext msgCtx;

        MessageContextPair(TOMMessage message, MessageContext msgCtx) {
            this.message = message;
            this.msgCtx = msgCtx;
        }
    }
    // replica ID
    private int id;
    // Server side comunication system
    private ServerCommunicationSystem cs = null;
    private ServerViewManager SVManager;
    private boolean isToJoin = false;
    private ReentrantLock waitTTPJoinMsgLock = new ReentrantLock();
    private Condition canProceed = waitTTPJoinMsgLock.newCondition();
    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */
    private ReentrantLock requestsLock = new ReentrantLock();
    private Executable executor = null;
    private Recoverable recoverer = null;
    private TOMLayer tomLayer = null;
    private boolean tomStackCreated = false;
    private ReplicaContext replicaCtx = null;


    /*******************************************************/
    /**
     * Constructor
     * @param id Replica ID
     */
    public ServiceReplica(int id, Executable executor, Recoverable recoverer) {
        this(id, "", executor, recoverer);
    }

    /**
     * Constructor
     * 
     * @param id Process ID
     * @param configHome Configuration directory for JBP
     */
    public ServiceReplica(int id, String configHome, Executable executor, Recoverable recoverer) {
        this.id = id;
        this.SVManager = new ServerViewManager(id, configHome);
        this.executor = executor;
        this.recoverer = recoverer;
        this.init();
    }

    //******* EDUARDO BEGIN **************//
    /**
     * Constructor
     * @param id Replica ID
     * @param isToJoin: if true, the replica tries to join the system, otherwise it waits for TTP message
     * informing its join
     */
    public ServiceReplica(int id, boolean isToJoin, Executable executor, Recoverable recoverer) {
        this(id, "", isToJoin, executor, recoverer);
    }

    public ServiceReplica(int id, String configHome, boolean isToJoin, Executable executor, Recoverable recoverer) {
        this.isToJoin = isToJoin;
        this.id = id;
        this.SVManager = new ServerViewManager(id, configHome);
        this.executor = executor;
        this.recoverer = recoverer;
        this.init();
    }
    
    

    //******* EDUARDO END **************//
    // this method initializes the object
    private void init() {
        try {
            cs = new ServerCommunicationSystem(this.SVManager, this);
        } catch (Exception ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to build a communication system.");
        }

        //******* EDUARDO BEGIN **************//

        if (this.SVManager.isInCurrentView()) {
            System.out.println("Esta na view atual: " + this.SVManager.getCurrentView());
            initTOMLayer(-1, -1); // initiaze the TOM layer
        } else {
            if (this.isToJoin) {
                System.out.println("Vai enviar join: " + this.SVManager.getCurrentView());
                //Não está na visão inicial e é para executar um join;
                int port = this.SVManager.getStaticConf().getServerToServerPort(id) - 1;
                String ip = this.SVManager.getStaticConf().getServerToServerRemoteAddress(id).getAddress().getHostAddress();
                ReconfigureReply r = null;
                Reconfiguration rec = new Reconfiguration(id);
                do {
                    //System.out.println("while 1");
                    rec.addServer(id, ip, port);

                    r = rec.execute();
                } while (!r.getView().isMember(id));
                rec.close();
                this.SVManager.processJoinResult(r);

                // initiaze the TOM layer
                initTOMLayer(r.getLastExecConsId(), r.getExecLeader());

                this.cs.updateServersConnections();
                this.cs.joinViewReceived();
            } else {
                //Não está na visão inicial e é apenas para aguardar pela view onde o join foi executado

                System.out.println("Vai aguardar a TTP: " + this.SVManager.getCurrentView());
                waitTTPJoinMsgLock.lock();
                try {
                    canProceed.awaitUninterruptibly();
                } finally {
                    waitTTPJoinMsgLock.unlock();
                }
            }


        }
        initReplica();
    }

    public void joinMsgReceived(TTPMessage msg) {
        ReconfigureReply r = msg.getReply();

        if (r.getView().isMember(id)) {
            this.SVManager.processJoinResult(r);

            initTOMLayer(r.getLastExecConsId(), r.getExecLeader()); // initiaze the TOM layer
            //this.startState = r.getStartState();
            cs.updateServersConnections();
            this.cs.joinViewReceived();
            waitTTPJoinMsgLock.lock();
            canProceed.signalAll();
            waitTTPJoinMsgLock.unlock();
        }
    }

    private void initReplica() {
        cs.start();
    }
    //******* EDUARDO END **************//

	/**
	 * This is the method invoked to deliver a totally ordered request.
	 *
	 * @param msg The request delivered by the delivery thread
	 */
	@Override
	public final void receiveReadonlyMessage(TOMMessage tomMsg, MessageContext msgCtx) {
		byte[] response = null;
		response = executor.executeUnordered(tomMsg.getContent(), msgCtx);

		// build the reply and send it to the client
		tomMsg.reply = new TOMMessage(id, tomMsg.getSession(),
				tomMsg.getSequence(), response, SVManager.getCurrentViewId());            
                cs.send(new int[]{tomMsg.getSender()}, tomMsg.reply);
	}

    public void receiveMessages(int consId, int regency, TOMMessage[] requests) {
		TOMMessage firstRequest = requests[0];

		if(executor instanceof BatchExecutable) {
    		//DEBUG
            navigators.smart.tom.util.Logger.println("BATCHEXECUTOR");
			
			int numRequests = 0;
			
			//Messages to put in the batch
			List<TOMMessage> toBatch = new ArrayList<TOMMessage>();
			
			//Message Contexts (one Context per message in the batch)
			List<MessageContext> msgCtxts = new ArrayList<MessageContext>();
			
			for (TOMMessage request : requests) {
				if (request.getViewID() == SVManager.getCurrentViewId()) {
					
					//If message is a request, put message in the toBatch list
					if (request.getReqType() == TOMMessageType.REQUEST) {
						numRequests++;
						
						//Make new message context
						MessageContext msgCtx = new MessageContext(
								firstRequest.timestamp, firstRequest.nonces,
								regency, consId, request.getSender(),
								firstRequest);
						
						//Put context in the message context list
						msgCtxts.add(msgCtx);
						
						request.deliveryTime = System.nanoTime();
						
						//Add message to the ToBatch list
						toBatch.add(request);
					} else if (request.getReqType() == TOMMessageType.RECONFIG) {
						// Reconfiguration request to be processed after the
						// batch
						SVManager.enqueueUpdate(request);
					} else {
						throw new RuntimeException("Should never reach here!");
					}
					
				} else {
					// message sender had an old view, resend the message to
					// him
					tomLayer.getCommunication().send(
							new int[] { request.getSender() },
							new TOMMessage(SVManager.getStaticConf()
									.getProcessId(), request.getSession(),
									request.getSequence(), TOMUtil
											.getBytes(SVManager
													.getCurrentView()),
									SVManager.getCurrentViewId()));
				}
			}
			
			//In the end, if there are messages in the Batch
			if(numRequests > 0){
				//Make new batch to deliver
				byte[][] batch = new byte[numRequests][];
				
				//Put messages in the batch
				int line = 0;
				for(TOMMessage m : toBatch){
					batch[line] = m.getContent();
				}
				
				MessageContext[] msgContexts = new MessageContext[msgCtxts.size()];
				
				msgContexts = msgCtxts.toArray(msgContexts);
				
				//Deliver the batch and wait for replies
				byte[][] replies = ((BatchExecutable) executor).executeBatch(batch, msgContexts);
				
				//Send the replies back to the client
				for(int index = 0; index < toBatch.size(); index++){
					TOMMessage request = toBatch.get(index);
					request.reply = new TOMMessage(id,
							request.getSession(), request.getSequence(),
							replies[index], SVManager.getCurrentViewId());
					cs.send(new int[] { request.getSender() }, request.reply);
				}
				
				//DEBUG
	            navigators.smart.tom.util.Logger.println("BATCHEXECUTOR END");
			}
    	} else {
                    
                navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) singe executor for consensus " + consId);
        	for (TOMMessage request: requests) {
        		if (request.getViewID() == SVManager.getCurrentViewId()) {
                            
                                navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) same view");
        			if (request.getReqType() == TOMMessageType.REQUEST) {
                                    
                                        navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) this is a REQUEST type message");
        				byte[] response = null;
        				//normal request execution
        				//create a context for the batch of messages to be delivered
        				MessageContext msgCtx = new MessageContext(firstRequest.timestamp, 
        						firstRequest.nonces, regency, consId, request.getSender(), firstRequest);
        				request.deliveryTime = System.nanoTime();
                                        
                                        navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) executing message " + request.getSequence() + " from " + request.getSender() + " decided in consensus " + consId);
        				response = ((SingleExecutable)executor).executeOrdered(request.getContent(), msgCtx);
                                        // build the reply and send it to the client
                                        request.reply = new TOMMessage(id, request.getSession(),
                                        request.getSequence(), response, SVManager.getCurrentViewId());
                                        
                                        navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) sending reply to " + request.getSender());
                                        cs.send(new int[]{request.getSender()}, request.reply);
        			} else if (request.getReqType() == TOMMessageType.RECONFIG) {
        				//Reconfiguration request to be processed after the batch
                                    navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) Enqueing an update");
        				SVManager.enqueueUpdate(request);
        			} else {
        				throw new RuntimeException("Should never reach here!");
        			}
        		} else {
                            
                                navigators.smart.tom.util.Logger.println("(ServiceReplica.receiveMessages) sending current view to " + request.getSender());
        			//message sender had an old view, resend the message to him
        			tomLayer.getCommunication().send(new int[]{request.getSender()},
        					new TOMMessage(SVManager.getStaticConf().getProcessId(),
        							request.getSession(), request.getSequence(),
        							TOMUtil.getBytes(SVManager.getCurrentView()), SVManager.getCurrentViewId()));
        		}
        	}
    	}
    }


	/**
     * This method makes the replica leave the group
     */
    public void leave() {
        ReconfigureReply r = null;
        Reconfiguration rec = new Reconfiguration(id);
        do {
            //System.out.println("while 1");
            rec.removeServer(id);

            r = rec.execute();
        } while (r.getView().isMember(id));
        rec.close();
        this.cs.updateServersConnections();
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DOS CHECKPOINTS */

    @Override
    public byte[] getState() { //TODO: Ha por aqui uma condicao de corrida!
        requestsLock.lock();
        byte[] state = recoverer.getState();
        requestsLock.unlock();
        return state;

    }

    @Override
    public void setState(byte[] state) {
        requestsLock.lock();
        recoverer.setState(state);
        requestsLock.unlock();
    }

    /**
     * This method initializes the object
     * 
     * @param cs Server side communication System
     * @param conf Total order messaging configuration
     */
    private void initTOMLayer(int lastExec, int lastLeader) {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }
       
        //******* EDUARDO BEGIN **************//
        int me = SVManager.getStaticConf().getProcessId(); // this process ID

        if (!SVManager.isInCurrentView()) {
            throw new RuntimeException("I'm not an acceptor!");
        }
        //******* EDUARDO END **************//
        
        // Assemble the total order messaging layer
        MessageFactory messageFactory = new MessageFactory(me);
        
        LeaderModule lm = new LeaderModule(SVManager);
                
        Acceptor acceptor = new Acceptor(cs, messageFactory, lm, SVManager);
        cs.setAcceptor(acceptor);
        
        Proposer proposer = new Proposer(cs, messageFactory, SVManager);

        ExecutionManager manager = new ExecutionManager(SVManager, acceptor, proposer, me);
        
        acceptor.setManager(manager);
        proposer.setManager(manager);

        tomLayer = new TOMLayer(manager, this, lm, acceptor, cs, SVManager);
        
        manager.setTOMLayer(tomLayer);
        
        //******* EDUARDO BEGIN **************//
        SVManager.setTomLayer(tomLayer);
        //******* EDUARDO END **************//
        
        cs.setTOMLayer(tomLayer);
        cs.setRequestReceiver(tomLayer);

        acceptor.setTOMLayer(tomLayer);

        if(SVManager.getStaticConf().isShutdownHookEnabled()){
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread(cs,lm,acceptor,manager,tomLayer));
        }
        tomLayer.start(); // start the layer execution
        tomStackCreated = true;
        
        replicaCtx = new ReplicaContext(cs, SVManager);
    }

    /**
     * Obtains the current replica context (getting access to several information
     * and capabilities of the replication engine).
     * 
     * @return this replica context
     */
    public final ReplicaContext getReplicaContext() {
        return replicaCtx;
    }

}
