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

import java.io.Serializable;
import java.security.SignedObject;
import java.util.Arrays;
import java.util.Collection;
import java.util.TreeSet;

/**
 * This class stands for a round of an execution of a consensus
 */
public class Round implements Serializable {

    private transient Execution execution; // Execution where the round belongs to
    private transient TimeoutTask timeoutTask; // Timeout ssociated with this round
    private int number; // Round's number
    private int me; // Process ID
    private boolean[] weakSetted;
    private boolean[] strongSetted;
    private byte[][] weak; // weakling accepted values from other processes
    private byte[][] strong; // strongly accepted values from other processes
    private byte[][] decide; // values decided by other processes
    private Collection<Integer> freeze = null; // processes where this round was freezed
    private boolean frozen = false; // is this round frozen?
    private boolean collected = false; // indicates if a collect message for this round was already sent
    private long timeout; // duration of the timeout
    private boolean alreadyRemoved = false; // indicates if this round was removed from its execution

    public byte[] propValue = null; // proposed value
    public Object deserializedPropValue = null; //utility var
    public byte[] propValueHash = null; // proposed value hash
    public SignedObject[] proofs; // proof from other processes
    
    /**
     * Creates a new instance of Round for acceptors
     * @param parent Execution to which this round belongs
     * @param number Number of the round
     * @param timeout Timeout duration for this round
     */
    protected Round(Execution parent, int number, long timeout) {
        this.execution = parent;
        this.number = number;

        ExecutionManager manager = execution.getManager();

        this.me = manager.getProcessId();

        int[] acceptors = manager.getAcceptors();
        int n = acceptors.length;

        weakSetted = new boolean[n];
        strongSetted = new boolean[n];
        
        Arrays.fill(weakSetted, false);
        Arrays.fill(strongSetted, false);

        if (number == 0) {
            this.weak = new byte[n][];
            this.strong = new byte[n][];
            this.decide = new byte[n][];

            Arrays.fill((Object[]) weak, null);
            Arrays.fill((Object[]) strong, null);
            Arrays.fill((Object[]) decide, null);
        } else {
            Round previousRound = execution.getRound(number - 1);

            this.weak = previousRound.getWeak();
            this.strong = previousRound.getStrong();
            this.decide = previousRound.getDecide();
        }

        //define the timeout for this round
        this.timeout = (int) Math.pow(2, number) * timeout;
        manager.getAcceptor().scheduleTimeout(this);
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

    /**
     * Adds a collect proof from another replica
     *
     * @param acceptor replica which sent the proof
     * @param proof proof received
     */
    public void setCollectProof(int acceptor, SignedObject proof) {
        if (proofs == null) {
            proofs = new SignedObject[weak.length];
            Arrays.fill((SignedObject[]) proofs, null);
        }

        proofs[acceptor] = proof;
    }

    /**
     * Retrieves the duration for the timeout
     * @return Duration for the timeout
     */
    public long getTimeout() {
        return this.timeout;
    }

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
     * Sets the timeout associated with this round
     * @param timeoutTask The timeout associated with this round
     */
    public void setTimeoutTask(TimeoutTask timeoutTask) {
        this.timeoutTask = timeoutTask;
    }

    /**
     * Retrieves the timeout associated with this round
     * @param timeoutTask The timeout associated with this round
     */
    public TimeoutTask getTimeoutTask() {
        return timeoutTask;
    }

    /**
     * Informs if there is a weakly accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a weakly accepted value from a replica, false otherwise
     */
    public boolean isWeakSetted(int acceptor) {
        return weak[acceptor] != null;
    }

    /**
     * Informs if there is a strongly accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a strongly accepted value from a replica, false otherwise
     */
    public boolean isStrongSetted(int acceptor) {
        return strong[acceptor] != null;
    }

    /**
     * Informs if there is a decided value from a replica
     * @param acceptor The replica ID
     * @return True if there is a decided value from a replica, false otherwise
     */
    public boolean isDecideSetted(int acceptor) {
        return decide[acceptor] != null;
    }

    /**
     * Retrives the weakly accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value weakly accepted from the specified replica
     */
    public byte[] getWeak(int acceptor) {
        return this.weak[acceptor];
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
    public void setWeak(int acceptor, byte[] value) { // TODO: Condicao de corrida?
        if (!weakSetted[acceptor] && !isFrozen()) { //it can only be setted once
            weak[acceptor] = value;
            weakSetted[acceptor] = true;
        }
    }

    /**
     * Retrives the strongly accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value strongly accepted from the specified replica
     */
    public byte[] getStrong(int acceptor) {
        return strong[acceptor];
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
    public void setStrong(int acceptor, byte[] value) { // TODO: condicao de corrida?
        if (!strongSetted[acceptor] && !isFrozen()) { //it can only be setted once
            strong[acceptor] = value;
            strongSetted[acceptor] = true;
        }
    }

    /**
     * Retrieves the decided value by the specified replica
     * @param acceptor The replica ID
     * @return The value decided by the specified replica
     */
    public byte[] getDecide(int acceptor) {
        return decide[acceptor];
    }

    /**
     * Retrives all the decided values by all replicas
     * @return The values decided by all replicas
     */
    public byte[][] getDecide() {
        return decide;
    }

    /**
     * Sets the decided value by the specified replica
     * @param acceptor The replica ID
     * @param value The value decided by the specified replica
     */
    public void setDecide(int acceptor, byte[] value) {
        decide[acceptor] = value;
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
     * Retrives the ammount of replicas that decided a specified value
     * @param value The value in question
     * @return Ammount of replicas that decided the specified value
     */
    public int countDecide(byte[] value) {
        return count(null,decide, value);
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
            buffWeak.append(str(weak[i]) + ",");
            buffStrong.append(str(strong[i]) + ",");
            buffDecide.append(str(decide[i]) + ",");
        }

        buffWeak.append(str(weak[weak.length - 1]) + ")");
        buffStrong.append(str(strong[strong.length - 1]) + ")");
        buffDecide.append(str(decide[decide.length - 1]) + ")");

        return "eid=" + execution.getId() + " r=" + getNumber() + " " + buffWeak + " " + buffStrong + " " + buffDecide;
    }

    private String str(byte[] obj) {
        if(obj == null) {
            return "*";
        } else {
            sun.misc.BASE64Encoder b64 = new sun.misc.BASE64Encoder();
            return b64.encode(obj);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
    }
}
