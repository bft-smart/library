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

package navigators.smart.paxosatwar.messages;

import navigators.smart.tom.util.SerialisationHelper;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 *
 * @author edualchieri
 *
 * This class represents the proof used in the rounds freeze processing.
 * The SignedObject contain the CollectProof for this server.
 */
public final class Proof {

    private CollectProof[] proofs; // Signed proofs
    private byte[] nextPropose; // next value to be proposed
 
    /**
     * Creates a new instance of Proof
     * @param proofs Signed proofs
     * @param nextPropose Next value to be proposed
     */
    public Proof(CollectProof[] proofs, byte[] nextPropose) {

        this.proofs = proofs;
        this.nextPropose = nextPropose;

    }

    public Proof(DataInput in) throws IOException{
        proofs = new CollectProof[in.readInt()];
        for (int i = 0; i < proofs.length; i++) {
            int pos = in.readInt();
            if(pos < 0){
                break;  //reached end
            }
            proofs[pos] = new CollectProof(in);
        }
        nextPropose = SerialisationHelper.readByteArray(in);
    }

    public void serialise(DataOutput out) throws IOException{
        out.writeInt(proofs.length);
        for (int i = 0; i < proofs.length; i++) {
            if(proofs[i]!=null){
               out.writeInt(i);
               proofs[i].serialise(out);
            }
        }
        out.writeInt(-1); //write end tag
        SerialisationHelper.writeByteArray(nextPropose, out);

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
    public CollectProof[] getProofs(){
        return this.proofs;
    }

}

