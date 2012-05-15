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

package bftsmart.tom.core.messages;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.Externalizable;
import java.io.IOException;

import bftsmart.communication.SystemMessage;
import bftsmart.tom.util.DebugInfo;

/**
 * This class represents a total ordered message
 */
public class TOMMessage extends SystemMessage implements Externalizable, Comparable {

	//******* EDUARDO BEGIN **************//
	private int viewID; //current sender view
	private TOMMessageType type; // request type: application or reconfiguration request
	//******* EDUARDO END **************//

	private int session; // Sequence number defined by the client
	private int sequence; // Sequence number defined by the client

	private byte[] content = null; // Content of the message

	//the fields bellow are not serialized!!!
	private transient int id; // ID for this message. It should be unique

	public transient long timestamp = 0; // timestamp to be used by the application
	public transient byte[] nonces = null; // nonces to be used by the applciation

	public transient int destination = -1; // message destination
	public transient boolean signed = false; // is this message signed?

	public transient long receptionTime;//the reception time of this message
	public transient boolean timeout = false;//this message was timed out?

	//the bytes received from the client and its MAC and signature
	public transient byte[] serializedMessage = null;
	public transient byte[] serializedMessageSignature = null;
	public transient byte[] serializedMessageMAC = null;

	//for benchmarking purposes
	public transient long consensusStartTime = 0; //time the consensus is created
	public transient long proposeReceivedTime = 0; //time the propose is received
	public transient long weakSentTime = 0; //time the replica' weak message is sent
	public transient long strongSentTime = 0; //time the replica' strong message is sent
	public transient long decisionTime = 0; //time the decision is established
	public transient long deliveryTime =0; //time the request is delivered
	public transient long executedTime =0; //time the request is executed

	//the reply associated with this message
	public transient TOMMessage reply = null;
	public transient boolean alreadyProposed = false;

	public TOMMessage() {
	}

	/**
	 * Creates a new instance of TOMMessage
	 *
	 * @param sender ID of the process which sent the message
	 * @param session Session id of the sender
	 * @param sequence Sequence number defined by the client
	 * @param content Content of the message
	 * @param view ViewId of the message
	 */
	public TOMMessage(int sender, int session, int sequence, byte[] content, int view) {
		this(sender,session,sequence,content, view, TOMMessageType.ORDERED_REQUEST);
	}

	/**
	 * Creates a new instance of TOMMessage
	 *
	 * @param sender ID of the process which sent the message
	 * @param session Session id of the sender
	 * @param sequence Sequence number defined by the client
	 * @param content Content of the message
	 * @param view ViewId of the message
	 * @param type Type of the request
	 */
	public TOMMessage(int sender, int session, int sequence, byte[] content, int view, TOMMessageType type) {
		super(sender);
		this.session = session;
		this.sequence = sequence;
		this.viewID = view;
		buildId();
		this.content = content;
		this.type = type;
	}



	/** THIS IS JOAO'S CODE, FOR DEBUGGING */
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
	 */
	public void  setDebugInfo(DebugInfo info) {
		this.info = info;
	}

	/****************************************************/

	/**
	 * Retrieves the session id of this message
	 * @return The session id of this message
	 */
	public int getSession() {
		return session;
	}

	/**
	 * Retrieves the sequence number defined by the client
	 * @return The sequence number defined by the client
	 */
	public int getSequence() {
		return sequence;
	}

	public int getViewID() {
		return viewID;
	}

	public TOMMessageType getReqType() {
		return type;
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
	 * Verifies if two TOMMessage are equal. For performance reasons, the method
	 * only verifies if the send and sequence are equal.
	 *
	 * Two TOMMessage are equal if they have the same sender, sequence number
	 * and content.
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
		return "(" + sender + "," + sequence + "," + session + ")";
	}

	public void wExternal(DataOutput out) throws IOException {
		out.writeInt(sender);
		out.writeInt(viewID);
		out.writeInt(type.toInt());
		out.writeInt(session);
		out.writeInt(sequence);

		if (content == null) {
			out.writeInt(-1);
		} else {
			out.writeInt(content.length);
			out.write(content);
		}
	}

	public void rExternal(DataInput in) throws IOException, ClassNotFoundException {
		sender = in.readInt();
		viewID = in.readInt();
		type = TOMMessageType.fromInt(in.readInt());
		session = in.readInt();
		sequence = in.readInt();

		int toRead = in.readInt();
		if (toRead != -1) {
			content = new byte[toRead];
			in.readFully(content);
		}

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
			 m.wExternal(dos);
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
			 m.rExternal(dis);
		 }catch(Exception e) {
			 System.out.println("deu merda "+e);
			 return null;
		 }

		 return m;
	 }

	 @Override
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

		 if (this.getSession() < tm.getSession())
			 return BEFORE;
		 if (this.getSession() > tm.getSession())
			 return AFTER;

		 if (this.getSequence() < tm.getSequence())
			 return BEFORE;
		 if (this.getSequence() > tm.getSequence())
			 return AFTER;

		 return EQUAL;
	 }
}
