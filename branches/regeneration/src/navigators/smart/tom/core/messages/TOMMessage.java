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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import navigators.smart.tom.util.DebugInfo;

/**
 * This class represents a total ordered message
 */
public class TOMMessage extends SystemMessage implements Externalizable, Comparable {

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
    public transient boolean includesClassHeader = false; //are class header serialized

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

    public TOMMessage() {
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
        super(sender);
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
        return "(" + sender + "," + sequence + ")";
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeInt(sequence);
        if (content == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(content.length);
            out.write(content);
        }
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        sequence = in.readInt();

        int toRead = in.readInt();
        if (toRead != -1) {
            content = new byte[toRead];

            do {
                toRead -= in.read(content, content.length - toRead, toRead);
            } while (toRead > 0);
        }

        buildId();
    }

    public void writeExternal(DataOutput out) throws IOException {
        out.writeInt(sender);
        out.writeInt(sequence);

        if (content == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(content.length);
            out.write(content);
        }

        out.writeBoolean(isReadOnlyRequest());
    }

    public void readExternal(DataInput in) throws IOException, ClassNotFoundException {
        sender = in.readInt();
        sequence = in.readInt();

        int toRead = in.readInt();
        if (toRead != -1) {
            content = new byte[toRead];

            in.readFully(content);
        }

        readOnlyRequest = in.readBoolean();

        buildId();
    }


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

    public static byte[] messageToBytes(TOMMessage m) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        try{
            m.writeExternal(dos);
            dos.flush();
        }catch(Exception e) {
        }
        return baos.toByteArray();
    }

    public static TOMMessage bytesToMessage(byte[] b) {
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        DataInputStream dis = new DataInputStream(bais);

        TOMMessage m = new TOMMessage();
        try{
            m.readExternal(dis);
        }catch(Exception e) {
            System.out.println("deu merda "+e);
            return null;
        }
        
        return m;
    }

    public int compareTo(Object o) {
        final int BEFORE = -1;
        final int EQUAL = 0;
        final int AFTER = 1;

        TOMMessage tm = (TOMMessage)o;

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

