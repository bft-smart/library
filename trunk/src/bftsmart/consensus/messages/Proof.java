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
package bftsmart.consensus.messages;

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

