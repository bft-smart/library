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

import java.nio.ByteBuffer;

import navigators.smart.tom.util.DebugInfo;
import navigators.smart.tom.util.SerialisationHelper;

/**
 * This class represents a total ordered message
 */
public class TOMMessage extends SystemMessage implements Comparable<TOMMessage> {

    private int sequence; // Sequence number defined by the client
    private byte[] content = null; // Content of the message
    private boolean readOnlyRequest = false; //this is a read only request

    //the fields bellow are not serialized!!!
    private transient int id; // ID for this message. It should be unique

    public transient long timestamp = 0; // timestamp to be used by the application
    public transient byte[] nonces = null; // nonces to be used by the applciation

    //Esses dois Campos servem pra que?
    public transient int destination = -1; // message destination
    public transient boolean signed = false; // is this message signed?
//    public transient boolean includesClassHeader = false; //are class header serialized

    public transient long receptionTime;//the reception time of this message
    public transient boolean timeout = false;//this message was timed out?

    //the bytes received from the client and its MAC and signature
    public transient byte[] serializedMessage = null;
    public transient byte[] serializedMessageSignature = null;
    public transient byte[] serializedMessageMAC = null;

    //for benchmarking purposes
    public transient long consensusStartTime=0;
    public transient long consensusExecutionTime=0;
    public transient int consensusBatchSize=0;
    public transient long requestTotalLatency=0;

    public TOMMessage(ByteBuffer in) {
        super(Type.TOM_MSG,in);
        sequence = in.getInt();
        content = SerialisationHelper.readByteArray(in);
        buildId();
    }

    /**
     * Creates a new instance of TOMMessage
     *
     * @param sender ID of the process which sent the message
     * @param sequence Sequence number defined by the client
     * @param content Content of the message
     */
    public TOMMessage(int sender, int sequence, byte[] content) {
        this(sender,sequence,content,false);
    }

    /**
     * Creates a new instance of TOMMessage
     *
     * @param sender ID of the process which sent the message
     * @param sequence Sequence number defined by the client
     * @param content Content of the message
     * @param readOnlyRequest it is a read only request
     */
    public TOMMessage(int sender, int sequence, byte[] content, boolean readOnlyRequest) {
        super(Type.TOM_MSG,sender);
        this.sequence = sequence;

        buildId();
        this.content = content;
        this.readOnlyRequest = readOnlyRequest;
    }

    /** ISTO E CODIGO DO JOAO, PARA TRATAR DE DEBUGGING */
    private transient DebugInfo info = null; // Debug information
    
    /**
     * Retrieves the debug info from the TOM layer
     * @return The debug info from the TOM layer
     */
    public DebugInfo getDebugInfo() {
        return info;
    }
    
    /**
     * Retrieves the debug info from the TOM layer
     * @return The debug info from the TOM layer
     */
    public void  setSequence(DebugInfo info) {
        this.info = info;
    }

    /****************************************************/

    /**
     * Retrieves the sequence number defined by the client
     * @return The sequence number defined by the client
     */
    public int getSequence() {
        return sequence;
    }

    /**
     * Retrieves the ID for this message. It should be unique
     * @return The ID for this message.
     */
    public int getId() {
        return id;
    }

    /**
     * Retrieves the content of the message
     * @return The content of the message
     */
    public byte[] getContent() {
        return content;
    }

    /**
     * @return the readOnlyRequest
     */
    public boolean isReadOnlyRequest() {
        return readOnlyRequest;
    }
    
    /**
     * Verifies if two TOMMessage objects are equal.
     *
     * Two TOMMessage are equal if they have the same sender,
     * sequence number and content.
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof TOMMessage)) {
            return false;
        }

        TOMMessage mc = (TOMMessage) o;

        return (mc.getSender() == sender) && (mc.getSequence() == sequence);
        /* return (mc.getSender() == sender) && (mc.getSequence() == sequence) &&
                Arrays.equals(mc.getContent(), this.content); */
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + this.sequence;
        hash = 59 * hash + this.getSender();
        return hash;
    }

    @Override
    public String toString() {
        return super.toString()+"(" + sender + "," + sequence + ")";
    }

    @Override
    public void serialise(ByteBuffer out) {
        super.serialise(out);
        out.putInt(sequence);
        SerialisationHelper.writeByteArray(content, out);
    }
    
    @Override
    public int getMsgSize(){
    	return super.getMsgSize()+ 8 + content.length; //4+4+content.length
    }
    

//    @Override
//    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
//
//    }

//    public void writeExternal(DataOutput out) throws IOException {
//        out.writeInt(sender);
//        out.writeInt(sequence);
//
//        if (content == null) {
//            out.writeInt(-1);
//        } else {
//            out.writeInt(content.length);
//            out.write(content);
//        }
//
//        out.writeBoolean(isReadOnlyRequest());
//    }

//    public void readExternal(DataInput in) throws IOException, ClassNotFoundException {
//        sender = in.readInt();
//        sequence = in.readInt();
//
//        int toRead = in.readInt();
//        if (toRead != -1) {
//            content = new byte[toRead];
//
//            in.readFully(content);
//        }
//
//        readOnlyRequest = in.readBoolean();
//
//        buildId();
//    }


    /**
     * Used to build an unique id for the message
     */
    private void buildId() {
        id = (sender << 20) | sequence;
    }

    /**
     * Retrieves the process ID of the sender given a message ID
     * @param id Message ID
     * @return Process ID of the sender
     */
    public static int getSenderFromId(int id) {
        return id >>> 20;
    }

//    public static byte[] messageToBytes(TOMMessage m) {
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
////        DataOutputStream dos = new DataOutputStream(baos);
//        try{
//            ObjectOutput oo = new ObjectOutputStream(baos);
//            m.serialise(oo);
//            oo.flush();
//        }catch(Exception e) {
//        }
//        return baos.toByteArray();
//    }
//
//    public static TOMMessage bytesToMessage(byte[] b) {
//        ByteBuffer buf = ByteBuffer.wrap(b);
//        TOMMessage m = new TOMMessage(buf);
//        return m;
//    }

    public int compareTo(TOMMessage tm) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        if (this.equals(tm))
            return EQUAL;

        if (this.getSender() < tm.getSender())
            return BEFORE;
        if (this.getSender() > tm.getSender())
            return AFTER;

        if (this.getSequence() < tm.getSequence())
            return BEFORE;
        if (this.getSequence() > tm.getSequence())
            return AFTER;

        return EQUAL;
    }
}

