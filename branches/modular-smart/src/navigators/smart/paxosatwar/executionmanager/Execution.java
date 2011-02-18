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

package navigators.smart.paxosatwar.executionmanager;

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.consensus.MeasuringConsensus;


/**
 * This class stands for an execution of a consensus
 */
public class Execution {

    private ExecutionManager manager; // Execution manager for this execution

    private MeasuringConsensus consensus; // MeasuringConsensus instance to which this execution works for
    private HashMap<Integer,Round> rounds = new HashMap<Integer,Round>(2);
    private ReentrantLock roundsLock = new ReentrantLock(); // Lock for concurrency control

    private boolean decided; // Is this execution decided?
    private long initialTimeout; // Initial timeout for rounds
    private int decisionRound = -1; // round at which a desision was made

    public ReentrantLock lock = new ReentrantLock(); //this execution lock (called by other classes)

    /**
     * Creates a new instance of Execution for Acceptor Manager
     * 
     * @param manager Execution manager for this execution
     * @param consensus MeasuringConsensus instance to which this execution works for
     * @param initialTimeout Initial timeout for rounds
     */
    protected Execution(ExecutionManager manager, MeasuringConsensus consensus, long initialTimeout) {
        this.manager = manager;
        this.consensus = consensus;
        this.initialTimeout = initialTimeout;
    }

    /**
     * This is the execution ID
     * @return Execution ID
     */
    public long getId() {
        return consensus.getId();
    }

    /**
     * This is the execution manager for this execution
     * @return Execution manager for this execution
     */
    public ExecutionManager getManager() {
        return manager;
    }

    /**
     * This is the consensus instance to which this execution works for
     * @return MeasuringConsensus instance to which this execution works for
     */
    public MeasuringConsensus getConsensus() { // TODO: Porque se chama getConsensus?
        return consensus;
    }

    /**
     * Gets a round associated with this execution
     * @param number The number of the round
     * @return The round
     */
    public Round getRound(int number) {
        return getRound(number,true);
    }

    /**
     * Gets a round associated with this execution
     * @param number The number of the round
     * @param create if the round is to be created if not existent
     * @return The round
     */
    public Round getRound(int number, boolean create) {
        roundsLock.lock();

        Round round = rounds.get(number);
        if(round == null && create){
            round = new Round(this, number, initialTimeout);
            rounds.put(number, round);
        }

        roundsLock.unlock();

        return round;
    }

    /**
     * Removes rounds greater than 'limit' from this execution
     * 
     * @param limit Rounds that should be kept (from 0 to 'limit')
     */
    public void removeRounds(int limit) {
        roundsLock.lock();

        for(Integer key : (Integer[])rounds.keySet().toArray(new Integer[0])) {
            if(key > limit) {
                Round round = rounds.remove(key);
                round.setRemoved();
                round.getTimeoutTask().cancel();
            }
        }

        roundsLock.unlock();
    }

    /**
     * The round at which a decision was possible to make
     * @return Round at which a decision was possible to make
     */
    public Round getDecisionRound() {
        roundsLock.lock();
        Round r = rounds.get(decisionRound);
        roundsLock.unlock();
        return r;
    }

    /**
     * The last round of this execution
     *
     * @return Last round of this execution
     */
    public Round getLastRound() {
        roundsLock.lock();
        Round r = rounds.get(rounds.size() - 1);
        roundsLock.unlock();
        return r;
    }

    /**
     * Informs wether or not the execution is decided
     *
     * @return True if it is decided, false otherwise
     */
    public boolean isDecided() {
        return decided;
    }

    /**
     * Called by the Acceptor, to set the decided value
     *
     * @param value The decided value
     * @param round The round at which a desision was made
     */
    public void decided(Round round/*, byte[] value*/) {
        if (!decided) {
            decided = true;
            decisionRound = round.getNumber();
            consensus.decided(round.propValue,decisionRound);
            consensus.executionTime = System.currentTimeMillis() - consensus.startTime;
            manager.getTOMLayer().decided(consensus);
        }
    }
}
