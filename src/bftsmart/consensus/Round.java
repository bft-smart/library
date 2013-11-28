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
package bftsmart.consensus;

import bftsmart.consensus.executionmanager.Execution;
import bftsmart.consensus.messages.PaxosMessage;
import java.io.Serializable;
import java.util.Arrays;
import org.apache.commons.codec.binary.Base64;

import bftsmart.reconfiguration.ServerViewController;
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
    private boolean[] writeSetted;
    private boolean[] acceptSetted;
    private byte[][] write; // WRITE values from other processes
    private byte[][] accept; // accepted values from other processes
    
    private boolean alreadyRemoved = false; // indicates if this round was removed from its execution

    public byte[] propValue = null; // proposed value
    public TOMMessage[] deserializedPropValue = null; //utility var
    public byte[] propValueHash = null; // proposed value hash
    public HashSet<PaxosMessage> proof; // proof from other processes

    private View lastView = null;

    private ServerViewController controller;

    /**
     * Creates a new instance of Round for acceptors
     * @param parent Execution to which this round belongs
     * @param number Number of the round
     * @param timeout Timeout duration for this round
     */
    public Round(ServerViewController controller, Execution parent, int number) {
        this.execution = parent;
        this.number = number;
        this.controller = controller;
        this.proof = new HashSet<PaxosMessage>();
        //ExecutionManager manager = execution.getManager();

        this.lastView = controller.getCurrentView();
        this.me = controller.getStaticConf().getProcessId();

        //int[] acceptors = manager.getAcceptors();
        int n = controller.getCurrentViewN();

        writeSetted = new boolean[n];
        acceptSetted = new boolean[n];

        Arrays.fill(writeSetted, false);
        Arrays.fill(acceptSetted, false);

        if (number == 0) {
            this.write = new byte[n][];
            this.accept = new byte[n][];

            Arrays.fill((Object[]) write, null);
            Arrays.fill((Object[]) accept, null);
        } else {
            Round previousRound = execution.getRound(number - 1, controller);

            this.write = previousRound.getWrite();
            this.accept = previousRound.getAccept();
        }
    }

    // If a view change takes place and concurrentely this consensus is still
    // receiving messages, the write and accept arrays must be updated
    private void updateArrays() {
        
        if (lastView.getId() != controller.getCurrentViewId()) {
            
            int n = controller.getCurrentViewN();
            
            byte[][] write = new byte[n][];
            byte[][] accept = new byte[n][];
            
            boolean[] writeSetted = new boolean[n];
            boolean[] acceptSetted = new boolean[n];

            Arrays.fill(writeSetted, false);
            Arrays.fill(acceptSetted, false);
        
            for (int pid : lastView.getProcesses()) {
                
                if (controller.isCurrentViewMember(pid)) {
                    
                    int currentPos = controller.getCurrentViewPos(pid);
                    int lastPos = lastView.getPos(pid);
                    
                    write[currentPos] = this.write[lastPos];
                    accept[currentPos] = this.accept[lastPos];

                    writeSetted[currentPos] = this.writeSetted[lastPos];
                    acceptSetted[currentPos] = this.acceptSetted[lastPos];

                }
            }
            
            this.write = write;
            this.accept = accept;

            this.writeSetted = writeSetted;
            this.acceptSetted = acceptSetted;

            lastView = controller.getCurrentView();
            
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
     * Informs if there is a WRITE value from a replica
     * @param acceptor The replica ID
     * @return True if there is a WRITE value from a replica, false otherwise
     */
    public boolean isWriteSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){
            return write[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Informs if there is a accepted value from a replica
     * @param acceptor The replica ID
     * @return True if there is a accepted value from a replica, false otherwise
     */
    public boolean isAcceptSetted(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){
            return accept[p] != null;
        }else{
            return false;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrives the WRITE value from the specified replica
     * @param acceptor The replica ID
     * @return The value from the specified replica
     */
    public byte[] getWrite(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){        
            return this.write[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves all WRITE value from all replicas
     * @return The values from all replicas
     */
    public byte[][] getWrite() {
        return this.write;
    }

    /**
     * Sets the WRITE value from the specified replica
     * @param acceptor The replica ID
     * @param value The valuefrom the specified replica
     */
    public void setWrite(int acceptor, byte[] value) { // TODO: Race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if (p >=0 /*&& !writeSetted[p] && !isFrozen() */) { //it can only be setted once
            write[p] = value;
            writeSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves the accepted value from the specified replica
     * @param acceptor The replica ID
     * @return The value accepted from the specified replica
     */
    public byte[] getAccept(int acceptor) {
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
         int p = this.controller.getCurrentViewPos(acceptor);
        if(p >= 0){        
        return accept[p];
        }else{
            return null;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves all accepted values from all replicas
     * @return The values accepted from all replicas
     */
    public byte[][] getAccept() {
        return accept;
    }

    /**
     * Sets the accepted value from the specified replica
     * @param acceptor The replica ID
     * @param value The value accepted from the specified replica
     */
    public void setAccept(int acceptor, byte[] value) { // TODO: race condition?
        
        updateArrays();
        
        //******* EDUARDO BEGIN **************//
        int p = this.controller.getCurrentViewPos(acceptor);
        if (p >= 0 /*&& !strongSetted[p] && !isFrozen()*/) { //it can only be setted once
            accept[p] = value;
            acceptSetted[p] = true;
        }
        //******* EDUARDO END **************//
    }

    /**
     * Retrieves the amount of replicas from which this process received a WRITE value
     * @param value The value in question
     * @return Amount of replicas from which this process received the specified value
     */
    public int countWrite(byte[] value) {
        return count(writeSetted,write, value);
    }

    /**
     * Retrieves the amount of replicas from which this process accepted a specified value
     * @param value The value in question
     * @return Amount of replicas from which this process accepted the specified value
     */
    public int countAccept(byte[] value) {
        return count(acceptSetted,accept, value);
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
        StringBuffer buffWrite = new StringBuffer(1024);
        StringBuffer buffAccept = new StringBuffer(1024);
        StringBuffer buffDecide = new StringBuffer(1024);

        buffWrite.append("W=(");
        buffAccept.append("S=(");
        buffDecide.append("D=(");

        for (int i = 0; i < write.length - 1; i++) {
            buffWrite.append(str(write[i]) + " [" + (write[i] != null ? write[i].length : 0) + " bytes] ,");
            buffAccept.append(str(accept[i]) + " [" + (accept[i] != null ? accept[i].length : 0) + " bytes] ,");
        }

        buffWrite.append(str(write[write.length - 1]) + " [" + (write[write.length - 1] != null ? write[write.length - 1].length : 0) + " bytes])");
        buffAccept.append(str(accept[accept.length - 1]) + " [" + (accept[accept.length - 1] != null ? accept[accept.length - 1].length : 0) + " bytes])");

        return "eid=" + execution.getId() + " r=" + getNumber() + " " + buffWrite + " " + buffAccept + " " + buffDecide;
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

        int n = controller.getCurrentViewN();
        
        writeSetted = new boolean[n];
        acceptSetted = new boolean[n];

        Arrays.fill(writeSetted, false);
        Arrays.fill(acceptSetted, false);

        this.write = new byte[n][];
        this.accept = new byte[n][];

        Arrays.fill((Object[]) write, null);
        Arrays.fill((Object[]) accept, null);
        
        this.proof = new HashSet<PaxosMessage>();
    }
}
