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
