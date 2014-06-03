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
package bftsmart.statemanagement.strategy;

import java.util.Collection;
import java.util.HashMap;

import bftsmart.reconfiguration.ServerViewController;
import bftsmart.reconfiguration.views.View;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.SMMessage;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.core.DeliveryThread;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;

/**
 * 
 * @author Marcel Santos
 *
 */
public abstract class BaseStateManager implements StateManager {
	
    protected TOMLayer tomLayer;
    protected ServerViewController SVController;
	protected DeliveryThread dt;
	
    protected HashMap<Integer, ApplicationState> senderStates = null;
    protected HashMap<Integer, View> senderViews = null;
    protected HashMap<Integer, Integer> senderRegencies = null;
    protected HashMap<Integer, Integer> senderLeaders = null;

    protected boolean appStateOnly;
    protected int waitingEid = -1;
    protected int lastEid;
    protected ApplicationState state;
    
    protected boolean isInitializing =  true;
    private HashMap<Integer, Integer> senderEids = null;
    
    public BaseStateManager() {
        senderStates = new HashMap<Integer, ApplicationState>();
        senderViews = new HashMap<Integer, View>();
        senderRegencies = new HashMap<Integer, Integer>();
        senderLeaders = new HashMap<Integer, Integer>();
    }
   
    protected int getReplies() {
        return senderStates.size();
    }

    protected boolean moreThanF_Replies() {
    	return senderStates.size() > SVController.getCurrentViewF();
    }

    protected boolean moreThan2F_Regencies(int regency) {
        return senderRegencies.size() > SVController.getQuorumAccept();
    }
    
    protected boolean moreThan2F_Leaders(int leader) {
        return senderLeaders.size() > SVController.getQuorumAccept();
    }

    protected boolean moreThan2F_Views(View view) {
    	Collection<View> views = senderViews.values();
    	int counter = 0;
    	for(View v : views) {
    		if(view.equals(v))
    			counter++;
    	}
        boolean result = counter > SVController.getQuorumAccept();
        views = null;
        return result;
    }
    
    /**
     * Clear the collections and state hold by this object.
     * Calls clear() in the States, Leaders, Regenviews and Views collections.
     * Sets the state to null; 
     */
    protected void reset() {
        senderStates.clear();
        senderLeaders.clear();
        senderRegencies.clear();
        senderViews.clear();
        state = null;
    }
    
    public Collection<ApplicationState> receivedStates() {
    	return senderStates.values();
    }
    
    public void setLastEID(int eid) {
    	lastEid = eid;
    }
    
    public int getLastEID() {
    	return lastEid;
    }
	
	@Override
    public void requestAppState(int eid) {
    	lastEid = eid + 1;
    	waitingEid = eid;
		System.out.println("waitingeid is now " + eid);
        appStateOnly = true;
        requestState();
    }

	@Override
    public void analyzeState(int eid) {
        Logger.println("(TOMLayer.analyzeState) The state transfer protocol is enabled");
        if (waitingEid == -1) {
            Logger.println("(TOMLayer.analyzeState) I'm not waiting for any state, so I will keep record of this message");
            if (tomLayer.execManager.isDecidable(eid)) {
                System.out.println("BaseStateManager.analyzeState: I have now more than " + SVController.getCurrentViewF() + " messages for EID " + eid + " which are beyond EID " + lastEid);
                lastEid = eid;
                waitingEid = eid - 1;
        		System.out.println("analyzeState " + waitingEid);
                requestState();
            }
        }
    }

	@Override
	public abstract void init(TOMLayer tomLayer, DeliveryThread dt);
	
	@Override
    public boolean isRetrievingState() {
		if(isInitializing) {
			return true;
		}
    	return waitingEid > -1;
    }
	
	@Override
	public void askCurrentConsensusId() {
		int me = SVController.getStaticConf().getProcessId();
		int[] target = SVController.getCurrentViewAcceptors();
			
		SMMessage currentEid = new StandardSMMessage(me, -1, TOMUtil.SM_ASK_INITIAL, 0, null, null, 0, 0);
		tomLayer.getCommunication().send(target, currentEid);
		
		target = SVController.getCurrentViewOtherAcceptors();
		
		while(isInitializing) {
			tomLayer.getCommunication().send(target, currentEid);
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void currentConsensusIdAsked(int sender) {
		int me = SVController.getStaticConf().getProcessId();
		int lastConsensusId = lastEid;
		SMMessage currentEidReply = new StandardSMMessage(me, lastConsensusId, TOMUtil.SM_REPLY_INITIAL, 0, null, null, 0, 0);
		tomLayer.getCommunication().send(new int[]{sender}, currentEidReply);
	}

	@Override
	public synchronized void currentConsensusIdReceived(SMMessage smsg) {
		if(!isInitializing || waitingEid > -1)
			return;
		if(senderEids == null) {
			senderEids = new HashMap<Integer, Integer>();
		}
		senderEids.put(smsg.getSender(), smsg.getEid());
		if(senderEids.size() >= SVController.getQuorumAccept()) {
			HashMap<Integer, Integer> eids = new HashMap<Integer, Integer>();
			for(int value : senderEids.values()) {
				Integer count = eids.get(value);
				if(count == null)
					eids.put(value, 0);
				else
					eids.put(value, count.intValue() + 1);
			}
			for(int key : eids.keySet()) {
				if(eids.get(key) >= SVController.getQuorumAccept()) {
					if(key == lastEid) {
						System.out.println("QUORUM OF REPLICAS REPLIED WITH EID " + key);
						dt.deliverLock();
						isInitializing = false;
						tomLayer.setLastExec(key);
						dt.canDeliver();
						dt.deliverUnlock();
						break;
					} else {
						//ask for state
						System.out.println("QUORUM " + key + " DIFFERENT FROM MY EID " + lastEid + ". WILL ASK FOR STATE");
		                lastEid = key + 1;
		                if(waitingEid == -1) {
			                waitingEid = key;
			                requestState();
		                }
					}
				}
			}
		}
	}

	protected abstract void requestState();
	
	@Override
	public abstract void stateTimeout();

	@Override
	public abstract void SMRequestDeliver(SMMessage msg, boolean isBFT);

	@Override
	public abstract void SMReplyDeliver(SMMessage msg, boolean isBFT);

}
