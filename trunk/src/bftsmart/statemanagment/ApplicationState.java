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

package bftsmart.statemanagment;

import java.io.Serializable;

/**
 * This classe represents a state tranfered from a replica to another. The state associated with the last
 * checkpoint together with all the batches of messages received do far, comprises the sender's
 * current state
 * 
 * IMPORTANT: The hash state MUST ALWAYS be present, regardless if the replica is supposed to
 * send the complete state or not
 * 
 * @author Jo�o Sousa
 */
public interface ApplicationState extends Serializable {

    /**
     * The consensus of the last batch of commands which the application was given
     * @return consensus of the last batch of commands which the application was given
     */
    public int getLastEid();
    
    /**
     * Indicates if the sender replica had the state requested by the recovering replica
     * @return true if the sender replica had the state requested by the recovering replica, false otherwise
     */
    public boolean hasState();

    /**
     * Sets a byte array that must be a representation of the application state
     * @param state a byte array that must be a representation of the application state
     */
    public void setSerializedState(byte[] state);
    
    /**
     * Byte array that must be a representation of the application state
     * @returns A byte array that must be a representation of the application state
     */
    public byte[] getSerializedState();
    
    /**
     * Gets an secure hash of the application state
     * @return Secure hash of the application state
     */
    public byte[] getStateHash();

    /**
     * This method MUST be implemented. However, the attribute returned by getSerializedState()
     * should be ignored, and getStateHash() should be used instead
     */
    public abstract boolean equals(Object obj);

    /**
     * This method MUST be implemented. However, the attribute returned by getSerializedState()
     * should be ignored, and getStateHash() should be used instead
     */
    public abstract int hashCode();
}
