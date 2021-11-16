package bftsmart.reconfiguration;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.LinkedList;

/**
 * @author robin
 */
public class BatchReconfigurationRequest implements Externalizable {
	private int sender;
	private LinkedList<String> joiningServers;
	private LinkedList<Integer> leavingServers;
	private int f = -1;
	private byte[] signature;

	public BatchReconfigurationRequest() {}

	public BatchReconfigurationRequest(int sender) {
		this.sender = sender;
		this.joiningServers = new LinkedList<>();
		this.leavingServers = new LinkedList<>();
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public byte[] getSignature() {
		return signature;
	}

	public LinkedList<String> getJoiningServers() {
		return joiningServers;
	}

	public LinkedList<Integer> getLeavingServers() {
		return leavingServers;
	}

	public int getSender() {
		return sender;
	}

	public int getF() {
		return f;
	}

	public void addServer(String server) {
		joiningServers.add(server);
	}

	public void removeServer(int server) {
		leavingServers.add(server);
	}

	public void setF(int f) {
		this.f = f;
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(sender);
		out.writeInt(signature.length);
		out.write(signature);
		out.writeInt(joiningServers.size());
		for (String joiningServer : joiningServers) {
			out.writeUTF(joiningServer);
		}
		out.writeInt(leavingServers.size());
		for (Integer leavingServer : leavingServers) {
			out.writeInt(leavingServer);
		}
		out.writeInt(f);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		sender = in.readInt();
		signature = new byte[in.readInt()];
		in.readFully(signature);
		int num = in.readInt();
		joiningServers = new LinkedList<>();
		while (num-- > 0) {
			joiningServers.add(in.readUTF());
		}

		num = in.readInt();
		leavingServers = new LinkedList<>();
		while (num-- > 0) {
			leavingServers.add(in.readInt());
		}

		f = in.readInt();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(sender);
		sb.append(joiningServers.size());
		for (String joiningServer : joiningServers) {
			sb.append(joiningServer);
		}
		sb.append(leavingServers.size());
		for (Integer leavingServer : leavingServers) {
			sb.append(leavingServer);
		}
		sb.append(f);
		return sb.toString();
	}

	public void clear() {
		joiningServers.clear();
		leavingServers.clear();
		f = -1;
		signature = null;
	}
}
