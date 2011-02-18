package navigators.smart.tom.util;

import java.nio.ByteBuffer;

import navigators.smart.tests.util.TestHelper;
import navigators.smart.tom.core.messages.TOMMessage;

import org.junit.Assert;
import org.junit.Test;

public class BatchBuilderReaderTest {

	@Test
	public void testCreateBatch() {
		BatchBuilder bb = new BatchBuilder();
		long timestamp = 2341;
		int numberOfNonces = 0;
		int numberOfMessages = 3;
		
		int totalMessageSize = 0; //total size of the messages being batched
        byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
        TOMMessage[] tommsgs = new TOMMessage[numberOfMessages];
        byte[][] signatures = null;

        // Fill the array of bytes for the messages/signatures being batched
        
        for (int i = 0; i<numberOfMessages; i++) {
            TOMMessage msg = new TOMMessage(0, 0, TestHelper.createTestByte());
            tommsgs[i] = msg;
            ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
            msg.serialise(buf);
            messages[i] = buf.array();
            totalMessageSize += messages[i].length;
        }
		
		
		
		byte[] batch = bb.createBatch(timestamp, numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
		
		
		BatchReader br = new BatchReader(batch, false);
		
		TOMMessage[] msges = br.deserialiseRequests();
		
		Assert.assertArrayEquals(tommsgs, msges);
	}
	@Test
	public void testCreateBatchWithSigs() {
		BatchBuilder bb = new BatchBuilder();
		long timestamp = 2341;
		int numberOfNonces = 0;
		int numberOfMessages = 3;
		
		int totalMessageSize = 0; //total size of the messages being batched
		byte[][] messages = new byte[numberOfMessages][]; //bytes of the message (or its hash)
		TOMMessage[] tommsgs = new TOMMessage[numberOfMessages];
		byte[][] signatures = null;
		
		// Fill the array of bytes for the messages/signatures being batched
		
		for (int i = 0; i<numberOfMessages; i++) {
			TOMMessage msg = new TOMMessage(0, 0, TestHelper.createTestByte());
			msg.serializedMessage= TestHelper.createTestByte();
			tommsgs[i] = msg;
			ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
			msg.serialise(buf);
			messages[i] = buf.array();
			totalMessageSize += messages[i].length;
		}
		
		
		
		byte[] batch = bb.createBatch(timestamp, numberOfNonces, numberOfMessages, totalMessageSize, messages, signatures);
		
		
		BatchReader br = new BatchReader(batch, false);
		
		TOMMessage[] msges = br.deserialiseRequests();
		
		Assert.assertArrayEquals(tommsgs, msges);
	}

}
