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

package navigators.smart.tom.core.messages;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * This is the super-class for all other kinds of messages created by JBP
 * TODO: Apenas sao criados objectos de sub-classes desta. Porque na otornar esta class abstract?
 * TODO: Esta classe nao se enquadra melhor no package de comunicacao?
 * 
 */

public abstract class SystemMessage  {

    public enum Type {

        TOM_MSG((byte) 1),
        FORWARDED((byte) 2),
        PAXOS_MSG((byte) 3),
        RR_MSG((byte) 4),
        RT_MSG((byte) 5),
        SM_MSG((byte)6);

        public final byte type;

        private static Map<Byte,Type> mapping = new HashMap<Byte, Type>();

        static{
            for(Type type:values()){
                mapping.put(type.type, type);
            }
        }

        Type (byte type) {
            this.type = type;
        }

        public static Type getByByte(byte type){
            return mapping.get(type);
        }
    }

    public final Type type;
    protected final int sender; // ID of the process which sent the message

    /**
     * Creates a new instance of SystemMessage
     * @param type The type id of this message
     * @param in The inputstream containing the serialised object
     * @throws IOException
     */
    public SystemMessage(Type type, DataInput in) throws IOException{
        this.type = type;
        sender = in.readInt();
    }
    
    /**
     * Creates a new instance of SystemMessage
     * @param type The type id of this message for preformant serialisation
     * @param sender ID of the process which sent the message
     */
    public SystemMessage(Type type, int sender){
        this.type = type;
        this.sender = sender;
    }
    
    /**
     * Returns the ID of the process which sent the message
     * @return
     */
    public final int getSender() {
        return sender;
    }

    /**
     * this method serialises the contents of this class
     * @param out
     * @throws IOException
     */
    protected void serialise(DataOutput out) throws IOException {
        out.writeByte(type.type);
        out.writeInt(sender);
    }

    public byte[] getBytes() throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream(248);
        DataOutputStream dos = new DataOutputStream(baos);
        serialise(dos);
        return baos.toByteArray();
    }
}
