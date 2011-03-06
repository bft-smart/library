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

package navigators.smart.paxosatwar;

import java.util.logging.Logger;

import navigators.smart.communication.MessageHandler;
import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.consensus.MeasuringConsensus;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.messages.PaWMessageHandler;
import navigators.smart.paxosatwar.requesthandler.OutOfContextMessageThread;
import navigators.smart.paxosatwar.requesthandler.timer.RequestsTimer;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.messages.TOMMessage;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class PaxosAtWarService implements ConsensusService{
	
	private static final Logger log = Logger.getLogger(PaxosAtWarService.class.getCanonicalName());
	
    /** Module managing the current and past leaders*/
    private final LeaderModule lm;

    /** Manages the seperate executions */
    private final ExecutionManager execmng;

    /** Manage timers for pending requests */
    public RequestsTimer requestsTimer;

    /** Handler for PaWMessages*/
	private PaWMessageHandler<?> msghandler;

    /**
     * Creates a new PaxosAtWar instance with the given modules that handle
     * several internal tasks
     * @param lm The LeaderManager
     * @param manager The ExecutionManager
     * @param msghandler The MessageHandler for PaxosAtWar Messages
     */
    public PaxosAtWarService(LeaderModule lm, ExecutionManager manager, PaWMessageHandler<?> msghandler){
        this.lm = lm;
        this.execmng = manager;
        this.msghandler = msghandler;
        //do not create a timer manager if the timeout is 0
        if (manager.getTOMLayer().getConf().getRequestTimeout()==0){
            this.requestsTimer = null;
        }
        else {
            // Create requests timers manager (a thread)
            this.requestsTimer = new RequestsTimer(manager.getRequestHandler(), manager.getTOMLayer().getConf().getRequestTimeout());
        }
    }

    public void decide(int execId, int batchsize, byte[] value) {
        MeasuringConsensus<?> cons = execmng.getExecution(execId).getConsensus();

        cons.batchSize = batchsize;
        execmng.getProposer().startExecution(execId, value);
    }

    @Override
    public long getLastExecuted() {
        return execmng.getRequestHandler().getLastExec();
    }

    @Override
    public void notifyNewRequest(TOMMessage msg) {
        requestsTimer.watch(msg);
        execmng.getRequestHandler().notifyNewRequest();
    }
    @Override
    public void notifyRequestDecided(TOMMessage msg){
        requestsTimer.unwatch(msg);
    }

    public MessageHandler<?,?> getMessageHandler() {
        return msghandler;
    }

    public int getId() {
        return execmng.getProcessId();
    }

    @Override
    public String toString(){
        return "Consensus in execution: " + execmng.getRequestHandler().getInExec() + " last executed consensus: "+execmng.getRequestHandler().getLastExec();
    }

    @Override
    public void deliverState(TransferableState state){
    	requestsTimer.unwatchAll(); //clear timer table TODO this is not fully BFT...
        long lastCheckpointEid = state.lastCheckpointEid;
        long lastEid = state.lastEid;
        if(state.leadermodulestate != null){
	        try {
				lm.setState(state.leadermodulestate);
			} catch (ClassNotFoundException e) {
				log.severe(e.getLocalizedMessage());
			}
        }
        //add leaderinfo of the last checkpoint
        lm.addLeaderInfo(lastCheckpointEid, state.lastCheckpointRound, state.lastCheckpointLeader);
        //add leaderinfo for previous message batches
        for (long eid = lastCheckpointEid + 1; eid <= lastEid; eid++) {
                lm.addLeaderInfo(eid, state.getMessageBatch(eid).round, state.getMessageBatch(eid).leader);
        }
        //deliver the state to executionmanager
        execmng.deliverState(state);
        
    }

    /**
     * @param cons The consensus of whom we wish to know the final proposer
     * @return The id of the final proposer
     */
    public int getProposer(Consensus<?> cons) {
        return lm.getLeader(cons.getId(), cons.getDecisionRound());
    }

    public void startDeliverState() {

    }

    public void deliveryFinished(Consensus<?> cons) {
        execmng.decided(cons);
    }

	@Override
	public void start() {
	}

	@Override
	public byte[] getState(Consensus<?> cons) {
		return lm.getState();
	}
}
