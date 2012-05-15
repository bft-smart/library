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

package bftsmart.paxosatwar.messages;

import java.io.Serializable;
import java.security.SignedObject;

/**
 *
 * @author edualchieri
 *
 * This class represents the proof used in the rounds freeze processing.
 * The SignedObject contain the CollectProof for this server.
 */
public final class Proof implements Serializable {

    private SignedObject[] proofs; // Signed proofs
    private byte[] nextPropose; // next value to be proposed
 
    /**
     * Creates a new instance of Proof
     * @param proofs Signed proofs
     * @param nextPropose Next value to be proposed
     */
    public Proof(SignedObject[] proofs, byte[] nextPropose) {

        this.proofs = proofs;
        this.nextPropose = nextPropose;

    }
    
    /**
     * Retrieves next value to be proposed
     * @return Next value to be proposed
     */
    public byte[] getNextPropose(){

        return this.nextPropose;

    }

    /**
     * Retrieves the signed proofs
     * @return Signed proofs
     */
    public SignedObject[] getProofs(){

        return this.proofs;

    }

}

