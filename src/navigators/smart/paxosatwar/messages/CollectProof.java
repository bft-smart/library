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

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import navigators.smart.tom.util.SerialisationHelper;

/**
 * Proofs to freezed consensus. This class can contain proofs for two consensus.
 * The freezed one, and the next one (if have).
 */
public final class CollectProof {

    // Proofs to freezed consensus
    private final FreezeProof proofIn;

    // Proofs to next consensus, if have next - after the freezed one
    private final FreezeProof proofNext;

    // The new leader id
    private final int newLeader;

    private byte[] signature;

    private byte[] serialisedForm;

    /**
     * Creates a new instance of CollectProof
     * @param proofIn Proofs to freezed consensus
     * @param proofNext Proofs to next consensus, if have next - after the freezed one
     * @param newLeader The new leader id
     */
    public CollectProof(FreezeProof proofIn, FreezeProof proofNext, int newLeader) {

        this.proofIn = proofIn;
        this.proofNext = proofNext;
        this.newLeader = newLeader;

    }
    
    /**
     * Retrieves the proof
     * @param in True for the proof of the freezed consensus, false for the proof of the next consensus
     * @return
     */
    public FreezeProof getProofs(boolean in){

        if(in){

            return this.proofIn;

        }else{

            return this.proofNext;

        }

    }
    
    /**
    * Retrieves the leader ID
    * @return The leader ID
    */
    public int getLeader(){

        return this.newLeader;

    }

    public CollectProof (DataInput in) throws IOException{
        proofIn = new FreezeProof(in);
        proofNext = new FreezeProof(in);
        newLeader = in.readInt();
        signature = SerialisationHelper.readByteArray(in);
    }

    public void serialise(DataOutput out) throws IOException{
        if(serialisedForm == null){
            proofIn.serialize(out);
            proofNext.serialize(out);
            out.writeInt(newLeader);
        } else {
            SerialisationHelper.writeByteArray(serialisedForm, out);
        }
        SerialisationHelper.writeByteArray(signature, out);
    }

    public byte[] getBytes() throws IOException {
        if(serialisedForm == null){
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(baos);
            //serialise without signature
            proofIn.serialize(out);
            proofNext.serialize(out);
            out.writeInt(newLeader);
            serialisedForm = baos.toByteArray();
        }
        return serialisedForm;
    }

    public void setSignature(byte[] sign) {
        signature = sign;
    }

    public byte[] getSignature() {
        return signature;
    }

}

