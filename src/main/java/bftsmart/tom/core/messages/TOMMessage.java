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
import bftsmart.tom.util.ServiceContent;
import org.slf4j.LoggerFactory;

/**
 * This class represents a total ordered message
 */
public class TOMMessage extends SystemMessage implements Externalizable, Comparable<TOMMessage> {

	//******* EDUARDO BEGIN **************//
	private int viewID; //current sender view
	private TOMMessageType type; // request type: application or reconfiguration request
	//******* EDUARDO END **************//

	private int session; // Sequence number defined by the client
	// Sequence number defined by the client.
	// There is a sequence number for ordered and another for unordered messages
	private int sequence;
	private int operationId; // Sequence number defined by the client

	private ServiceContent content; // The content of the message
	private byte metadata; // Optional metadata of the message
	private boolean hasReplicaSpecificContent;

	//the fields bellow are not serialized!!!
	private transient int id; // ID for this message. It should be unique

	public transient long timestamp; // timestamp to be used by the application

	public transient long seed; // seed for the nonces
	public transient int numOfNonces; // number of nonces

	public transient int destination = -1; // message destination
	public transient boolean signed; // is this message signed?

	public transient long receptionTime;//the reception time of this message (nanoseconds)
	public transient long receptionTimestamp;//the reception timestamp of this message (milliseconds)

	public transient boolean timeout;//this message was timed out?

	public transient boolean recvFromClient; // Did the client already sent this message to me, or did it arrive in the batch?
	public transient boolean isValid; // Was this request already validated by the replica?

	//the bytes received from the client and its MAC and signature
	public transient byte[] serializedMessage;
	public transient byte[] serializedMessageSignature;
	public transient byte[] serializedMessageMAC;

	//for benchmarking purposes
	public transient long consensusStartTime; //time the consensus is created
	public transient long proposeReceivedTime; //time the propose is received
	public transient long writeSentTime; //time the replica' write message is sent
	public transient long acceptSentTime; //time the replica' accept message is sent
	public transient long decisionTime; //time the decision is established
	public transient long deliveryTime; //time the request is delivered
	public transient long executedTime; //time the request is executed

	//the reply associated with this message
	public transient TOMMessage reply = null;
	public transient boolean alreadyProposed = false;
	public transient int retry = 4;

	private int replyServer = -1;

	public TOMMessage() {
	}

	/**
	 * Creates a new instance of TOMMessage with metadata as -1.
	 *
	 * @param sender The sender id
	 * @param session The session id of the sender
	 * @param sequence The sequence number created based on the message type
	 * @param operationId The operation sequence number disregarding message type
	 * @param commonContent The common content of the message
	 * @param hasReplicaSpecificContent Indicates if the message has or will have replica specific content
	 * @param view The view in which the message was sent
	 * @param type Type of the message
	 */
	public TOMMessage(int sender, int session, int sequence, int operationId,
					  byte[] commonContent, boolean hasReplicaSpecificContent, int view, TOMMessageType type) {
		this(sender, session, sequence, operationId, commonContent, hasReplicaSpecificContent, (byte) -1, view, type);
	}

	/**
	 * Creates a new instance of TOMMessage with metadata.
	 *
	 * @param sender The sender id
	 * @param session The session id of the sender
	 * @param sequence The sequence number created based on the message type
	 * @param operationId The operation sequence number disregarding message type
	 * @param commonContent The common content of the message
	 * @param hasReplicaSpecificContent Indicates if the message has or will have replica specific content
	 * @param metadata Metadata of the message
	 * @param view The view in which the message was sent
	 * @param type Type of the message
	 */
	public TOMMessage(int sender, int session, int sequence, int operationId,
					  byte[] commonContent, boolean hasReplicaSpecificContent,
					  byte metadata, int view, TOMMessageType type) {
		super(sender);
		this.session = session;
		this.sequence = sequence;
		this.operationId = operationId;
		this.viewID = view;
		this.hasReplicaSpecificContent = hasReplicaSpecificContent;
		buildId();
		this.content = new ServiceContent(commonContent);
		this.metadata = metadata;
		this.type = type;
	}

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

	public int getOperationId() {
		return operationId;
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
	public ServiceContent getContent() {
		return content;
	}

	/**
	 * Retrieves the common content of the message
	 * @return The common content of the message
	 */
	public byte[] getCommonContent() {
		return content.getCommonContent();
	}


	/**
	 * Indicate if the message has or should have replica specific content
	 * @return True if the message has or should have replica specific content, false otherwise
	 */
	public boolean hasReplicaSpecificContent() {
		return hasReplicaSpecificContent;
	}

	/**
	 * Retrieves the replica specific content of the message
	 * @return The replica specific content of the message
	 */
	public byte[] getReplicaSpecificContent() {
		return content.getReplicaSpecificContent();
	}


	/**
	 * Sets the replica specific content of the message
	 * @param replicaSpecificContent The replica specific content of the message
	 */
	public void setReplicaSpecificContent(byte[] replicaSpecificContent) {
		this.content.setReplicaSpecificContent(replicaSpecificContent);
	}

	/**
	 * Retrieves the metadata of the message
	 * @return The metadata of the message
	 */
	public byte getMetadata() {
		return metadata;
	}

	/**
	 * Verifies if two TOMMessage are equal. For performance reasons, the method
	 * only verifies if the send and sequence are equal.
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

		return (mc.getSender() == sender) && (mc.getSequence() == sequence) && (mc.getOperationId() == operationId);
	}

	@Override
	public int hashCode() {
		return this.id;
	}

	@Override
	public String toString() {
		return "[" + sender + ":" + session + ":" + sequence + "]";
	}

	public void wExternal(DataOutput out) throws IOException {
		out.writeInt(sender);
		out.writeInt(viewID);
		out.writeByte(type.ordinal());
		out.writeInt(session);
		out.writeInt(sequence);
		out.writeInt(operationId);
		out.writeInt(replyServer);

		out.writeInt(content.getCommonContent() == null ? -1 : content.getCommonContent().length);
		out.write(content.getCommonContent());
		out.writeBoolean(hasReplicaSpecificContent);
		out.write(metadata);
	}

	public void rExternal(DataInput in) throws IOException {
		sender = in.readInt();
		viewID = in.readInt();
		type = TOMMessageType.getMessageType(in.readByte());
		session = in.readInt();
		sequence = in.readInt();
		operationId = in.readInt();
		replyServer = in.readInt();

		int toRead = in.readInt();
		if (toRead != -1) {
			byte[] commonContent = new byte[toRead];
			in.readFully(commonContent);
			content = new ServiceContent(commonContent);
		}
		hasReplicaSpecificContent = in.readBoolean();
		metadata = in.readByte();

		buildId();
	}

	/**
	 * Used to build an unique id for the message
	 */
	private void buildId() {
		//id = (sender << 20) | sequence;
		int hash = 5;
		hash = 59 * hash + this.getSender();
		hash = 59 * hash + this.sequence;
		hash = 59 * hash + this.session;
		id = hash;
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
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 DataOutputStream dos = new DataOutputStream(baos)) {
			m.wExternal(dos);
			dos.flush();
			return baos.toByteArray();
		} catch(Exception e) {
			LoggerFactory.getLogger(TOMMessage.class).error("Failed to serialize TOMMessage", e);
			return null;
		}
	}

	public static TOMMessage bytesToMessage(byte[] b) {
		try (ByteArrayInputStream bais = new ByteArrayInputStream(b);
			 DataInputStream dis = new DataInputStream(bais)) {
			TOMMessage m = new TOMMessage();
			m.rExternal(dis);
			return m;
		} catch(Exception e) {
			LoggerFactory.getLogger(TOMMessage.class).error("Failed to deserialize TOMMessage",e);
			return null;
		}
	}

	@Override
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

		if (this.getSession() < tm.getSession())
			return BEFORE;
		if (this.getSession() > tm.getSession())
			return AFTER;

		if (this.getSequence() < tm.getSequence())
			return BEFORE;
		if (this.getSequence() > tm.getSequence())
			return AFTER;

		return Integer.compare(this.getOperationId(), tm.getOperationId());

	}

	public TOMMessage createCopy() {
		TOMMessage clone = new TOMMessage(sender, session, sequence, operationId, content.getCommonContent(),
				hasReplicaSpecificContent, metadata, viewID, type);

		clone.setReplyServer(replyServer);

		clone.acceptSentTime = this.acceptSentTime;
		clone.alreadyProposed = this.alreadyProposed;
		clone.authenticated = this.authenticated;
		clone.consensusStartTime = this.consensusStartTime;
		clone.decisionTime = this.decisionTime;
		clone.deliveryTime = this.deliveryTime;
		clone.destination = this.destination;
		clone.executedTime = this.executedTime;
		clone.isValid = this.isValid;
		clone.numOfNonces = this.numOfNonces;
		clone.proposeReceivedTime = this.proposeReceivedTime;
		clone.receptionTime = this.receptionTime;
		clone.receptionTimestamp = this.receptionTimestamp;
		clone.recvFromClient = this.recvFromClient;
		clone.reply = this.reply;
		clone.seed = this.seed;
		clone.serializedMessage = this.serializedMessage;
		clone.serializedMessageMAC = this.serializedMessageMAC;
		clone.serializedMessageSignature = this.serializedMessageSignature;
		clone.signed = this.signed;
		clone.timeout = this.timeout;
		clone.timestamp = this.timestamp;
		clone.writeSentTime = this.writeSentTime;
		clone.retry = this.retry;

		return clone;

	}


	public int getReplyServer() {
		return replyServer;
	}


	public void setReplyServer(int replyServer) {
		this.replyServer = replyServer;
	}
}
