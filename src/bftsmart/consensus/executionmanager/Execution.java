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
package bftsmart.consensus.executionmanager;

import bftsmart.consensus.Epoch;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.consensus.Consensus;
import bftsmart.reconfiguration.ServerViewController;



/**
 * This class stands for an execution of a consensus
 */
public class Execution {

    private ExecutionManager manager; // Execution manager for this execution

    private Consensus consensus; // Consensus instance to which this execution works for
    private HashMap<Integer,Epoch> epochs = new HashMap<Integer,Epoch>(2);
    private ReentrantLock epochsLock = new ReentrantLock(); // Lock for concurrency control

    private boolean decided; // Is this execution decided?
    private int decisionEpoch = -1; // epoch at which a decision was made

    //NEW ATTRIBUTES FOR THE LEADER CHANGE
    private int ets = 0;
    private TimestampValuePair quorumWrites = null;
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
     * Gets a epoch associated with this execution
     * @param timestamp The timestamp of the epoch
     * @return The epoch
     */
    public Epoch getEpoch(int timestamp, ServerViewController controller) {
        return getEpoch(timestamp,true, controller);
    }

    /**
     * Gets a epoch associated with this execution
     * @param timestamp The number of the epoch
     * @param create if the epoch is to be created if not existent
     * @return The epoch
     */
    public Epoch getEpoch(int timestamp, boolean create, ServerViewController controller) {
        epochsLock.lock();

        Epoch epoch = epochs.get(timestamp);
        if(epoch == null && create){
            epoch = new Epoch(controller, this, timestamp);
            epochs.put(timestamp, epoch);
        }

        epochsLock.unlock();

        return epoch;
    }
    
    /**
     * Increments the ETS of this replica, thus advancing 
     * to the next epoch of the consensus
     */
    public void incEts() {
        ets++;
    }
    
    /**
     * Returns the timestamp for the current epoch
     * @return the timestamp for the current epoch
     */
    public int getEts() {
        return ets;
    }
    
    /**
     * Store the value read from a Byzantine quorum of WRITES
     * @param value
     */
    public void setQuorumWrites(byte[] value) {

        quorumWrites = new TimestampValuePair(ets, value);
    }

    /**
     * Return the value read from a Byzantine quorum of WRITES that has
     * previously been stored
     * @return the value read from a Byzantine quorum of WRITES, if such
     * value has been obtained already
     */
    public TimestampValuePair getQuorumWrites() {
        return quorumWrites;
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
     * Creates an epoch associated with this execution, with the specified timestamp
     * @param timestamp The timestamp to associated to this epoch
     * @param recManager The replica's ServerViewController
     * @return The epoch
     */
    public Epoch createEpoch(int timestamp, ServerViewController recManager) {
        epochsLock.lock();

        Epoch epoch = new Epoch(recManager, this, timestamp);
        epochs.put(timestamp, epoch);

        epochsLock.unlock();

        return epoch;
    }
    /**
     * Creates a epoch associated with this execution, supposedly the next
     * @param recManager The replica's ServerViewController
     * @return The epoch
     */
    public Epoch createEpoch(ServerViewController recManager) {
        epochsLock.lock();

        Set<Integer> keys = epochs.keySet();

        int max = -1;
        for (int k : keys) {
            if (k > max) max = k;
        }

        max++;
        Epoch epoch = new Epoch(recManager, this, max);
        epochs.put(max, epoch);

        epochsLock.unlock();

        return epoch;
    }

    /**
     * Removes epochs greater than 'limit' from this execution
     *
     * @param limit Epochs that should be kept (from 0 to 'limit')
     */
    public void removeEpochs(int limit) {
        epochsLock.lock();

        for(Integer key : (Integer[])epochs.keySet().toArray(new Integer[0])) {
            if(key > limit) {
                Epoch epoch = epochs.remove(key);
                epoch.setRemoved();
                //epoch.getTimeoutTask().cancel();
            }
        }

        epochsLock.unlock();
    }

    /**
     * The epoch at which a decision was possible to make
     * @return Epoch at which a decision was possible to make
     */
    public Epoch getDecisionEpoch() {
        epochsLock.lock();
        Epoch r = epochs.get(decisionEpoch);
        epochsLock.unlock();
        return r;
    }

    /**
     * The last epoch of this execution
     *
     * @return Last epoch of this execution
     */
    public Epoch getLastEpoch() {
        epochsLock.lock();
        if (epochs.isEmpty()) {
            epochsLock.unlock();
            return null;
        }
        //Epoch epoch = epochs.get(epochs.size() - 1);
        Epoch epoch = epochs.get(ets); // the last epoch corresponds to the current ETS
        epochsLock.unlock();
        return epoch;
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
     * @param epoch The epoch at which a decision was made
     */
    public void decided(Epoch epoch, byte[] value) {
        if (!decided) {
            decided = true;
            decisionEpoch = epoch.getTimestamp();
            consensus.decided(epoch);
            manager.getTOMLayer().decided(consensus);
        }
    }
}
