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

import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.Serialisable;
import navigators.smart.tom.util.SerialisationHelper;

/**
 *
 * @author edualchieri
 *
 * This class represents the proof used in the rounds freeze processing.
 * The SignedObject contain the CollectProof for this server.
 */
public final class Proof implements Serialisable {

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

    public Proof(ByteBuffer in){
        proofs = new CollectProof[in.getInt()];
        for (int i = 0; i < proofs.length; i++) {
            int pos = in.getInt();
            if(pos < 0){
                break;  //reached end
            }
            proofs[pos] = new CollectProof(in);
        }
        nextPropose = SerialisationHelper.readByteArray(in);
    }

    /**
     * TODO this can be shrunken without tags if the length is sent 
     */
    public void serialise(ByteBuffer out) {
        out.putInt(proofs.length);
        for (int i = 0; i < proofs.length; i++) {
            if(proofs[i]!=null){
               out.putInt(i);
               proofs[i].serialise(out);
            }
        }
        out.putInt(-1); //put end tag
        SerialisationHelper.writeByteArray(nextPropose, out);

    }
    
    @Override
	public int getMsgSize() {
    	 int ret = 12;
         for (int i = 0; i < proofs.length; i++) {
             if(proofs[i]!=null){
            	 ret += 4;
            	 ret += proofs[i].getMsgSize();
             }
         }
        ret += nextPropose.length;
		return ret;
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

