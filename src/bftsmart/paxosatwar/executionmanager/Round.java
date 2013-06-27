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

import bftsmart.paxosatwar.messages.PaxosMessage;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;
import org.apache.commons.codec.binary.Base64;

import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.messages.TOMMessage;
import java.util.HashSet;
import java.util.Set;

/**
 * This class stands for a round of an execution of a consensus
 */
public class Round implements Serializable {

	private static final long serialVersionUID = -2891450035863688295L;
	private transient Execution execution; // Execution where the round belongs to
    
    private int number; // Round's number
    private int me; // Process ID
    private boolean[] weakSetted;
    private boolean[] strongSetted;
    private byte[][] weak; // weakling accepted values from other processes
    private byte[][] strong; // strongly accepted values from other processes
    private Collection<Integer> freeze = null; // processes where this round was freezed
    private boolean frozen = false; // is this round frozen?
    private boolean collected = false; // indicates if a collect message for this round was already sent
    
    private boolean alreadyRemoved = false; // indicates if this round was removed from its execution

    public byte[] propValue = null; // proposed value
    public TOMMessage[] deserializedPropValue = null; //utility var
    public byte[] propValueHash = null; // proposed value hash
    public HashSet<PaxosMessage> proof; // proof from other processes

    private View lastView = null;

    private ServerViewManager manager;

    /**
     * Creates a new instance of Round for acceptors
     * @param parent Execution to which this round belongs
     * @param number Number of the round
     * @param timeout Timeout duration for this round
     */
    protected Round(ServerViewManager manager, Execution parent, int number) {
        this.execution = parent;
        this.number = number;
        this.manager = manager;
        this.proof = new HashSet<PaxosMessage>();
        //ExecutionManager manager = execution.getManager();

        this.lastView = manager.getCurrentView();
        this.me = manager.getStaticConf().getProcessId();

        //int[] acceptors = manager.getAcceptors();
        int n = manager.getCurrentViewN();

        weakSetted = new boolean[n];
        strongSetted = new boolean[n];

        Arrays.fill(weakSetted, false);
        Arrays.fill(strongSetted, false);

        if (number == 0) {
            this.weak = new byte[n][];
            this.strong = new byte[n][];

            Arrays.fill((Object[]) weak, null);
            Arrays.fill((Object[]) strong, null);
        } else {
            Round previousRound = execution.getRound(number - 1, manager);

            this.weak = previousRound.getWeak();
            this.strong = previousRound.getStrong();
        }
    }

    // If a view change takes place and concurrentely this consensus is still
    // receiving messages, the weak and strong arrays must be updated
    private void updateArrays() {
        
        if (lastView.getId() != manager.getCurrentViewId()) {
            
            int n = manager.getCurrentViewN();
            
            byte[][] weak = new byte[n][];
            byte[][] strong = new byte[n][];
            
            boolean[] weakSetted = new boolean[n];
            boolean[] strongSetted = new boolean[n];

            Arrays.fill(weakSetted, false);
            Arrays.fill(strongSetted, false);
        
            for (int pid : lastView.getProcesses()) {
                
                if (manager.isCurrentViewMember(pid)) {
                    
                    int currentPos = manager.getCurrentViewPos(pid);
                    int lastPos = lastView.getPos(pid);
                    
                    weak[currentPos] = this.weak[lastPos];
                    strong[currentPos] = this.strong[lastPos];

                    weakSetted[currentPos] = this.weakSetted[lastPos];
                    strongSetted[currentPos] = this.strongSetted[lastPos];

                }
            }
            
            this.weak = weak;
            this.strong = strong;

            this.weakSetted = weakSetted;
            this.strongSetted = strongSetted;

            lastView = manager.getCurrentView();
            
        }
    }
            
    /**
     * Set this round as removed from its execution
     */
    public void setRemoved() {
        this.alreadyRemoved = true;
    }

    /**
     * Informs if this round was removed from its execution
     * @return True if it is removed, false otherwise
     */
    public boolean isRemoved() {
        return this.alreadyRemoved;
    }


    public void addToProof(PaxosMessage pm) {
        proof.add(pm);
    }
    
    public Set<PaxosMessage> getProof() {
        return proof;
    }
    /**
     * Retrieves the duration for the timeout
     * @return Duration for the timeout
     */
    /*public long getTimeout() {
        return this.timeout;
    }*/

    /**
     * Retrieves this round's number
     * @return This round's number
     */
    public int getNumber() {
        return number;
    }

    /**
     * Retrieves this round's execution
     * @return This round's execution
     */
    public Execution getExecution() {
        return execution;
    }

    /**
     * Informs if there is a weakly accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a weakly accepted value from a replica, false otherwise
     */
    public boolean isWeakSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.manager.getCurrentViewPos(acceptor);
        if(p >= 0){
            return weak[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Informs if there is a strongly accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a strongly accepted value from a replica, false otherwise
     */
    public boolean isStrongSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.manager.getCurrentViewPos(acceptor);
        if(p >= 0){
            return strong[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives the weakly accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value weakly accepted from the specified replica
     */
    public byte[] getWeak(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.manager.getCurrentViewPos(acceptor);
        if(p >= 0){        
            return this.weak[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives all weakly accepted value from all replicas
     * @return The values weakly accepted from all replicas
     */
    public byte[][] getWeak() {
        return this.weak;
    }

    /**
     * Sets the weakly accepted value from the specified replica
     * @param acceptor The replica ID
     * @param value The value weakly accepted from the specified replica
     */
    public void setWeak(int acceptor, byte[] value) { // TODO: Race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.manager.getCurrentViewPos(acceptor);
        if (p >=0 && /*!weakSetted[p] &&*/ !isFrozen()) { //it can only be setted once
            weak[p] = value;
            weakSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives the strongly accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value strongly accepted from the specified replica
     */
    public byte[] getStrong(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
         int p = this.manager.getCurrentViewPos(acceptor);
        if(p >= 0){        
        return strong[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives all strongly accepted values from all replicas
     * @return The values strongly accepted from all replicas
     */
    public byte[][] getStrong() {
        return strong;
    }

    /**
     * Sets the strongly accepted value from the specified replica
     * @param acceptor The replica ID
     * @param value The value strongly accepted from the specified replica
     */
    public void setStrong(int acceptor, byte[] value) { // TODO: race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.manager.getCurrentViewPos(acceptor);
        if (p >= 0 /*&& !strongSetted[p]*/ && !isFrozen()) { //it can only be setted once
            strong[p] = value;
            strongSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Indicates if a collect message for this round was already sent
     * @return True if done so, false otherwise
     */
    public boolean isCollected() {
        return collected;
    }

    /**
     * Establishes that a collect message for this round was already sent
     */
    public void collect() {
        collected = true;
    }

    /**
     * Indicates if this round is frozen
     * @return True if so, false otherwise
     */
    public boolean isFrozen() {
        return frozen;
    }

    /**
     * Establishes that this round is frozen
     */
    public void freeze() {
        frozen = true;
        addFreeze(me);
    }

    /**
     * Establishes that a replica locally freezed this round
     * @param acceptor replica that locally freezed this round
     */
    public void addFreeze(int acceptor) {
        if (freeze == null) {
            freeze = new TreeSet<Integer>();
        }
        freeze.add(acceptor);
    }

    /**
     * Retrieves the ammount of replicas that locally freezed this round
     * @return Ammount of replicas that locally freezed this round
     */
    public int countFreeze() {
        return freeze.size();
    }

    /**
     * Retrives the ammount of replicas from which this process weakly accepted a specified value
     * @param value The value in question
     * @return Ammount of replicas from which this process weakly accepted the specified value
     */
    public int countWeak(byte[] value) {
        return count(weakSetted,weak, value);
    }

    /**
     * Retrives the ammount of replicas from which this process strongly accepted a specified value
     * @param value The value in question
     * @return Ammount of replicas from which this process strongly accepted the specified value
     */
    public int countStrong(byte[] value) {
        return count(strongSetted,strong, value);
    }

    /**
     * Counts how many times 'value' occurs in 'array'
     * @param array Array where to count
     * @param value Value to count
     * @return Ammount of times that 'value' was find in 'array'
     */
    private int count(boolean[] arraySetted,byte[][] array, byte[] value) {
        if (value != null) {
            int counter = 0;
            for (int i = 0; i < array.length; i++) {
                if (arraySetted != null && arraySetted[i] && Arrays.equals(value, array[i])) {
                    counter++;
                }
            }
            return counter;
        }
        return 0;
    }

    /*************************** DEBUG METHODS *******************************/
    /**
     * Print round information.
     */
    @Override
    public String toString() {
        StringBuffer buffWeak = new StringBuffer(1024);
        StringBuffer buffStrong = new StringBuffer(1024);
        StringBuffer buffDecide = new StringBuffer(1024);

        buffWeak.append("W=(");
        buffStrong.append("S=(");
        buffDecide.append("D=(");

        //recall that weak.length = strong.length = decide.length

        for (int i = 0; i < weak.length - 1; i++) {
            buffWeak.append(str(weak[i]) + " [" + (weak[i] != null ? weak[i].length : 0) + " bytes] ,");
            buffStrong.append(str(strong[i]) + " [" + (strong[i] != null ? strong[i].length : 0) + " bytes] ,");
        }

        buffWeak.append(str(weak[weak.length - 1]) + " [" + (weak[weak.length - 1] != null ? weak[weak.length - 1].length : 0) + " bytes])");
        buffStrong.append(str(strong[strong.length - 1]) + " [" + (strong[strong.length - 1] != null ? strong[strong.length - 1].length : 0) + " bytes])");

        return "eid=" + execution.getId() + " r=" + getNumber() + " " + buffWeak + " " + buffStrong + " " + buffDecide;
    }

    private String str(byte[] obj) {
        if(obj == null) {
            return "*";
        } else {
            return Base64.encodeBase64String(obj);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
    
    /**
     * Clear all round info.
     */
    public void clear() {

        int n = manager.getCurrentViewN();
        
        weakSetted = new boolean[n];
        strongSetted = new boolean[n];

        Arrays.fill(weakSetted, false);
        Arrays.fill(strongSetted, false);

        this.weak = new byte[n][];
        this.strong = new byte[n][];

        Arrays.fill((Object[]) weak, null);
        Arrays.fill((Object[]) strong, null);
        
        this.proof = new HashSet<PaxosMessage>();
    }
}
