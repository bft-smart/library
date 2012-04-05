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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

/**
 * This class associates a round to a value
 * 
 * @author Joao Sousa
 */
public class TimestampValuePair implements Externalizable {

    private int timestamp; // timestamp
    private byte[] value; // value
    private byte[] hashedValue; // hash of the value
    

    /**
     * Constructor
     * @param round Round
     * @param value Value
     */
    public TimestampValuePair(int timestamp, byte[] value) {
        this.timestamp = timestamp;
        this.value = value;

        this.hashedValue = new byte[0];
    }

    /**
     * Empty construtor
     */
    public TimestampValuePair() {
        this.timestamp = -1;
        this.value = new byte[0];

        this.hashedValue = new byte[0];
    }
    /**
     * Set the value's hash
     * @param hashedValue Sintese do valor
     */
    public void setHashedValue(byte[] hashedValue) {
        this.hashedValue = hashedValue;
    }

    /**
     * Get the value's hash
     * @return hash of the value
     */
    public byte[] getHashedValue() {
        return hashedValue;
    }

    /**
     * Get round
     * @return Round
     */
    public int getRound() {
        return timestamp;
    }

    /**
     * Get value
     * @return Value
     */
    public byte[] getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof TimestampValuePair) {
            return ((TimestampValuePair) o).timestamp == timestamp;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return timestamp;
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException{

        out.writeInt(timestamp);
        out.writeObject(value);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{

        timestamp = in.readInt();
        value = (byte[]) in.readObject();
    }
}
