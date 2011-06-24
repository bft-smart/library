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
 * Esta classe associa um round a um valor
 * 
 * @author Joao Sousa
 */
public class RoundValuePair implements Externalizable {

    private int round; // round
    private byte[] value; // valor
    private byte[] hashedValue; // sintese do valor


    /**
     * Construtor
     * @param round Round
     * @param value Valor
     */
    public RoundValuePair(int round, byte[] value) {
        this.round = round;
        this.value = value;

        this.hashedValue = new byte[0];
    }

    /**
     * Construtor vazio
     */
    public RoundValuePair() {
        this.round = -1;
        this.value = new byte[0];

        this.hashedValue = new byte[0];
    }
    /**
     * Defenir sintese do valor
     * @param hashedValue Sintese do valor
     */
    public void setHashedValue(byte[] hashedValue) {
        this.hashedValue = hashedValue;
    }

    /**
     * Obter sintese do valor
     * @return sintese do valor
     */
    public byte[] getHashedValue() {
        return hashedValue;
    }

    /**
     * Obter round
     * @return Round
     */
    public int getRound() {
        return round;
    }

    /**
     * Obter valor
     * @return Valor
     */
    public byte[] getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RoundValuePair) {
            return ((RoundValuePair) o).round == round;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return round;
    }
    
    @Override
    public void writeExternal(ObjectOutput out) throws IOException{

        out.writeInt(round);
        out.writeObject(value);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException{

        round = in.readInt();
        value = (byte[]) in.readObject();
    }
}
