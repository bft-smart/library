/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
