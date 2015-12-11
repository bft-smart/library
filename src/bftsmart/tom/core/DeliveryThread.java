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
package bftsmart.tom.core;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.consensus.Decision;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.leaderchange.CertifiedDecision;
import bftsmart.tom.server.Recoverable;
import bftsmart.tom.util.BatchReader;
import bftsmart.tom.util.Logger;
import java.util.HashSet;
import java.util.Set;

/**
 * This class implements a thread which will deliver totally ordered requests to the application
 * 
 */
public final class DeliveryThread extends Thread {

    private LinkedBlockingQueue<Decision> decided = new LinkedBlockingQueue<Decision>(); // decided from consensus
    private TOMLayer tomLayer; // TOM layer
    private ServiceReplica receiver; // Object that receives requests from clients
    private Recoverable recoverer; // Object that uses state transfer
    private ServerViewController controller;
    private Lock decidedLock = new ReentrantLock();
    private Condition notEmptyQueue = decidedLock.newCondition();

    /**
     * Creates a new instance of DeliveryThread
     * @param tomLayer TOM layer
     * @param receiver Object that receives requests from clients
     * @param conf TOM configuration
     */
    public DeliveryThread(TOMLayer tomLayer, ServiceReplica receiver, Recoverable recoverer, ServerViewController controller) {
        super("Delivery Thread");

        this.tomLayer = tomLayer;
        this.receiver = receiver;
        this.recoverer = recoverer;
        //******* EDUARDO BEGIN **************//
        this.controller = controller;
        //******* EDUARDO END **************//
    }

    
   public Recoverable getRecoverer() {
        return recoverer;
    }
   
    /**
     * Invoked by the TOM layer, to deliver a decision
     * @param dec Decision established from the consensus
     */
    public void delivery(Decision dec) {
        if (!containsGoodReconfig(dec)) {

            Logger.println("(DeliveryThread.delivery) Decision from consensus " + dec.getConsensusId() + " does not contain good reconfiguration");
            //set this decision as the last one from this replica
            tomLayer.setLastExec(dec.getConsensusId());
            //define that end of this execution
            tomLayer.setInExec(-1);
        } //else if (tomLayer.controller.getStaticConf().getProcessId() == 0) System.exit(0);
        try {
        	decidedLock.lock();
            decided.put(dec);
            
			// clean the ordered messages from the pending buffer
            TOMMessage[] requests = extractMessagesFromDecision(dec);
			tomLayer.clientsManager.requestsOrdered(requests);
            
            notEmptyQueue.signalAll();
            decidedLock.unlock();
            Logger.println("(DeliveryThread.delivery) Consensus " + dec.getConsensusId() + " finished. Decided size=" + decided.size());
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private boolean containsGoodReconfig(Decision dec) {
        TOMMessage[] decidedMessages = dec.getDeserializedValue();

        for (TOMMessage decidedMessage : decidedMessages) {
            if (decidedMessage.getReqType() == TOMMessageType.RECONFIG
                    && decidedMessage.getViewID() == controller.getCurrentViewId()) {
                return true;
            }
        }
        return false;
    }

    /** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
    private ReentrantLock deliverLock = new ReentrantLock();
    private Condition canDeliver = deliverLock.newCondition();

    public void deliverLock() {
    	// release the delivery lock to avoid blocking on state transfer
		decidedLock.lock();
		notEmptyQueue.signalAll();
		decidedLock.unlock();
    	
        deliverLock.lock();
    }

    public void deliverUnlock() {
        deliverLock.unlock();
    }

    public void canDeliver() {
        canDeliver.signalAll();
    }

    public void update(ApplicationState state) {
       
        int lastEid =  recoverer.setState(state);

        //set this decision as the last one from this replica
        System.out.println("Setting last EID to " + lastEid);
        tomLayer.setLastExec(lastEid);

        //define the last stable consensus... the stable consensus can
        //be removed from the leaderManager and the executionManager
        if (lastEid > 2) {
            int stableConsensus = lastEid - 3;
            tomLayer.execManager.removeOutOfContexts(stableConsensus);
        }

        //define that end of this execution
        //stateManager.setWaiting(-1);
        tomLayer.setNoExec();

        System.out.print("Current decided size: " + decided.size());
        decided.clear();

        System.out.println("(DeliveryThread.update) All finished up to " + lastEid);
    }

    /**
     * This is the code for the thread. It delivers decisions to the TOM
     * request receiver object (which is the application)
     */
    @Override
    public void run() {
        while (true) {
  			/** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
  			deliverLock();
  			while (tomLayer.isRetrievingState()) {
  				System.out.println("(DeliveryThread.run) Retrieving State.");
  				canDeliver.awaitUninterruptibly();
  				System.out.println("(DeliveryThread.run) canDeliver released.");
  			}
  			try {
  				ArrayList<Decision> decisions = new ArrayList<Decision>();
  				decidedLock.lock();
  				if(decided.isEmpty()) {
  					notEmptyQueue.await();
  				}
  				decided.drainTo(decisions);
  				decidedLock.unlock();
  				if (decisions.size() > 0) {
  					TOMMessage[][] requests = new TOMMessage[decisions.size()][];
					int[] consensusIds = new int[requests.length];
                                        int[] leadersIds = new int[requests.length];
                                        int[] regenciesIds = new int[requests.length];
                                        CertifiedDecision[] proofs;
                                        proofs = new CertifiedDecision[requests.length];
  					int count = 0;
  					for (Decision d : decisions) {
  						requests[count] = extractMessagesFromDecision(d);
						consensusIds[count] = d.getConsensusId();
                                                leadersIds[count] = d.getLeader();
                                                regenciesIds[count] = d.getRegency();
                                                
                                                CertifiedDecision led = new CertifiedDecision(this.controller.getStaticConf().getProcessId(),
                                                        d.getConsensusId(), d.getValue(), d.getDecisionEpoch().proof);
                                                proofs[count] = led;
                                                
  						// cons.firstMessageProposed contains the performance counters
  						if (requests[count][0].equals(d.firstMessageProposed)) {
  	                    	long time = requests[count][0].timestamp;
  							requests[count][0] = d.firstMessageProposed;
  	                        requests[count][0].timestamp = time;
  						}
  						
  						count++;
  					}

  					Decision lastDecision = decisions.get(decisions.size() - 1);

  					if (requests != null && requests.length > 0) {
  						deliverMessages(consensusIds, regenciesIds, leadersIds, proofs, requests);

  						// ******* EDUARDO BEGIN ***********//
  						if (controller.hasUpdates()) {
  							processReconfigMessages(lastDecision.getConsensusId());

  							// set the consensus associated to the last decision as the last executed
  							tomLayer.setLastExec(lastDecision.getConsensusId());
  							// define that end of this execution
  							tomLayer.setInExec(-1);
  							// ******* EDUARDO END **************//
  						}
  					}

  					// define the last stable consensus... the stable consensus can
  					// be removed from the leaderManager and the executionManager
  					// TODO: Is this part necessary? If it is, can we put it
  					// inside setLastExec
  					int eid = lastDecision.getConsensusId();
  					if (eid > 2) {
  						int stableConsensus = eid - 3;

  						tomLayer.lm.removeStableConsenusInfos(stableConsensus);
  						tomLayer.execManager.removeConsensus(stableConsensus);
  					}
  				}
  			} catch (Exception e) {
  				e.printStackTrace(System.err);
  			}

  			/** THIS IS JOAO'S CODE, TO HANDLE STATE TRANSFER */
  			deliverUnlock();
  			/******************************************************************/
  		}
    }
    
    private TOMMessage[] extractMessagesFromDecision(Decision dec) {
    	TOMMessage[] requests = (TOMMessage[]) dec.getDeserializedValue();
    	if (requests == null) {
    		// there are no cached deserialized requests
    		// this may happen if this batch proposal was not verified
    		// TODO: this condition is possible?

    		Logger.println("(DeliveryThread.run) interpreting and verifying batched requests.");

    		// obtain an array of requests from the decisions obtained
    		BatchReader batchReader = new BatchReader(dec.getValue(),
    				controller.getStaticConf().getUseSignatures() == 1);
    		requests = batchReader.deserialiseRequests(controller);
    	} else {
    		Logger.println("(DeliveryThread.run) using cached requests from the propose.");
    	}

    	return requests;
    }
    
    protected void deliverUnordered(TOMMessage request, int regency) {
        MessageContext msgCtx = new MessageContext(System.currentTimeMillis(),
                0,0, regency, -1, -1, null, request.getSender(), null, false);
        msgCtx.readOnly = true;
        receiver.receiveReadonlyMessage(request, msgCtx);
    }

    private void deliverMessages(int consId[], int regencies[], int leaders[], CertifiedDecision[] proofs, TOMMessage[][] requests) {
        receiver.receiveMessages(consId, regencies, leaders, proofs, requests);
    }

    private void processReconfigMessages(int consId) {
        byte[] response = controller.executeUpdates(consId);
        TOMMessage[] dests = controller.clearUpdates();

        for (int i = 0; i < dests.length; i++) {
            tomLayer.getCommunication().send(new int[]{dests[i].getSender()},
                    new TOMMessage(controller.getStaticConf().getProcessId(),
                    dests[i].getSession(), dests[i].getSequence(), response,
                    controller.getCurrentViewId(),TOMMessageType.RECONFIG));
        }

        tomLayer.getCommunication().updateServersConnections();
    }

}
