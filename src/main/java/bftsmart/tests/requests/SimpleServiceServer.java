package bftsmart.tests.requests;

import bftsmart.tests.util.Operation;
import bftsmart.tom.MessageContext;
import bftsmart.tom.ServiceReplica;
import bftsmart.tom.server.defaultservices.DefaultSingleRecoverable;

import java.nio.ByteBuffer;

public class SimpleServiceServer extends DefaultSingleRecoverable {
	private byte[] state;

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("USAGE: bftsmart.tests.requests.SimpleServer <state size> <process id>");
			System.exit(-1);
		}
		int stateSize = Integer.parseInt(args[0]);
		int processId = Integer.parseInt(args[1]);
		new SimpleServiceServer(processId, stateSize);
	}

	public SimpleServiceServer(int processId, int stateSize) {
		state = new byte[stateSize];
		for (int i = 0; i < stateSize; i++) {
			state[i] = (byte) i;
		}
		new ServiceReplica(processId, this, this);
	}
	@Override
	public void installSnapshot(byte[] state) {
		this.state = state;
	}

	@Override
	public byte[] getSnapshot() {
		return state;
	}

	@Override
	public byte[] appExecuteOrdered(byte[] command, MessageContext msgCtx) {
		ByteBuffer buffer = ByteBuffer.wrap(command);
		Operation op = Operation.getOperation(buffer.get());
		byte[] response = null;
		switch (op) {
			case PUT:
				response = new byte[0];
				break;
			case GET:
				response = state;
				break;
		}
		return response;
	}

	@Override
	public byte[] appExecuteUnordered(byte[] command, MessageContext msgCtx) {
		ByteBuffer buffer = ByteBuffer.wrap(command);
		Operation op = Operation.getOperation(buffer.get());
		byte[] response = null;
		if (op == Operation.GET) {
			response = state;
		}
		return response;
	}
}
