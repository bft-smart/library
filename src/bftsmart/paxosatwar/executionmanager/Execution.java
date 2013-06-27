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
package bftsmart.paxosatwar.executionmanager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.paxosatwar.Consensus;
import bftsmart.reconfiguration.ServerViewManager;



/**
 * This class stands for an execution of a consensus
 */
public class Execution {

    private ExecutionManager manager; // Execution manager for this execution

    private Consensus consensus; // Consensus instance to which this execution works for
    private HashMap<Integer,Round> rounds = new HashMap<Integer,Round>(2);
    private ReentrantLock roundsLock = new ReentrantLock(); // Lock for concurrency control

    private boolean decided; // Is this execution decided?
    private int decisionRound = -1; // round at which a desision was made

    //NEW ATTRIBUTES FOR THE LEADER CHANGE
    private int ets = 0;
    private TimestampValuePair quorumWeaks = new TimestampValuePair(0, new byte[0]);
    private HashSet<TimestampValuePair> writeSet = new HashSet<TimestampValuePair>();

    public ReentrantLock lock = new ReentrantLock(); //this execution lock (called by other classes)

    /**
     * Creates a new instance of Execution for Acceptor Manager
     *
     * @param manager Execution manager for this execution
     * @param consensus Consensus instance to which this execution works for
     */
    protected Execution(ExecutionManager manager, Consensus consensus) {
        this.manager = manager;
        this.consensus = consensus;
    }

    /**
     * This is the execution ID
     * @return Execution ID
     */
    public int getId() {
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
     * @return Consensus instance to which this execution works for
     */
    public Consensus getLearner() { // TODO: Why it is called getLearner?
        return consensus;
    }

    /**
     * Gets a round associated with this execution
     * @param number The number of the round
     * @return The round
     */
    public Round getRound(int number, ServerViewManager recManager) {
        return getRound(number,true, recManager);
    }

    /**
     * Gets a round associated with this execution
     * @param number The number of the round
     * @param create if the round is to be created if not existent
     * @return The round
     */
    public Round getRound(int number, boolean create, ServerViewManager recManager) {
        roundsLock.lock();

        Round round = rounds.get(number);
        if(round == null && create){
            round = new Round(recManager, this, number);
            rounds.put(number, round);
        }

        roundsLock.unlock();

        return round;
    }
    
    /**
     * Increment the ETS of this replica
     */
    public void incEts() {
        ets++;
    }

    /**
     * Store the value read from a Byzantine quorum of WEAKS
     * @param value
     */
    public void setQuorumWeaks(byte[] value) {

        quorumWeaks = new TimestampValuePair(ets, value);
    }

    /**
     * Return the value read from a Byzantine quorum of WEAKS that has
     * previously been stored
     * @return the value read from a Byzantine quorum of WEAKS, if such
     * value has been obtained already
     */
    public TimestampValuePair getQuorumWeaks() {
        return quorumWeaks;
    }

    /**
     * Add a value that shall be written to the writeSet
     * @param value Value to write to the writeSet
     */
    public void addWritten(byte[] value) {

        writeSet.add(new TimestampValuePair(ets, value));
    }

    /**
     * Remove an already writte value from  writeSet
     * @param value valor a remover do writeSet
     */
    public void removeWritten(byte[] value) {

        for (TimestampValuePair rv : writeSet) {

            if (Arrays.equals(rv.getValue(), value)) writeSet.remove(rv);
        }

    }
    public HashSet<TimestampValuePair> getWriteSet() {
        return writeSet;
    }
    /**
     * Creates a round associated with this execution, supposedly the next
     * @return The round
     */
    public Round createRound(ServerViewManager recManager) {
        roundsLock.lock();

        Set<Integer> keys = rounds.keySet();

        int max = -1;
        for (int k : keys) {
            if (k > max) max = k;
        }

        max++;
        Round round = new Round(recManager, this, max);
        rounds.put(max, round);

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
                //round.getTimeoutTask().cancel();
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
        if (rounds.isEmpty()) {
            roundsLock.unlock();
            return null;
        }
        Round r = rounds.get(rounds.size() - 1);
        roundsLock.unlock();
        return r;
    }

    /**
     * Informs whether or not the execution is decided
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
     * @param round The round at which a decision was made
     */
    public void decided(Round round, byte[] value) {
        if (!decided) {
            decided = true;
            decisionRound = round.getNumber();
            consensus.decided(round);
            manager.getTOMLayer().decided(consensus);
        }
    }
}
