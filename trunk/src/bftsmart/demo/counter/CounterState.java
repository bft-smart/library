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
package bftsmart.demo.counter;

import java.util.Arrays;

import bftsmart.statemanagement.ApplicationState;

/**
 *
 * @author Joao Sousa
 */
public class CounterState implements ApplicationState {
    
    private byte[] state; // State associated with the last checkpoint
    private byte[] stateHash; // Hash of the state associated with the last checkpoint
    private int lastEid = -1; // Execution ID for the last messages batch delivered to the application
    private boolean hasState; // indicates if the replica really had the requested state

    /**
     * Constructs a TansferableState
     * This constructor should be used when there is a valid state to construct the object with
     * @param messageBatches Batches received since the last checkpoint.
     * @param state State associated with the last checkpoint
     * @param stateHash Hash of the state associated with the last checkpoint
     */
    public CounterState(int lastEid, byte[] state, byte[] stateHash) {
       
        this.lastEid = lastEid; // Execution ID for the last messages batch delivered to the application
        this.state = state; // State associated with the last checkpoint
        this.stateHash = stateHash;
        this.hasState = true;
    }

    /**
     * Constructs a TansferableState
     * This constructor should be used when there isn't a valid state to construct the object with
     */
    public CounterState() {
        
        this.lastEid = -1;
        this.state = null; // State associated with the last checkpoint
        this.stateHash = null;
        this.hasState = false;
    }
    

    @Override
    public int getLastEid() {
        return lastEid;
    }

    @Override
    public boolean hasState() {
        return hasState;
    }

    @Override
    public void setSerializedState(byte[] state) {
        this.state = state;
    }

    @Override
    public byte[] getSerializedState() {
        return state;
    }

    @Override
    public byte[] getStateHash() {
        return stateHash;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CounterState) {
            CounterState tState = (CounterState) obj;
            
            return (Arrays.equals(this.stateHash, tState.stateHash) &&
                    tState.lastEid == this.lastEid && tState.hasState == this.hasState);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        hash = hash * 31 + this.lastEid;
        hash = hash * 31 + (this.hasState ? 1 : 0);
        if (this.stateHash != null) {
            for (int i = 0; i < this.stateHash.length; i++) hash = hash * 31 + (int) this.stateHash[i];
        } else {
            hash = hash * 31 + 0;
        }
        return hash;
    }
}
