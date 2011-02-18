package navigators.smart.paxosatwar.messages;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

import navigators.smart.tests.util.TestHelper;

import org.junit.Test;

public class PaxosMessageTest {

	@Test
	public void testSerialiseFreeze() {
		PaxosMessage msg = new PaxosMessage(MessageFactory.FREEZE,0,0,0);
		
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		PaxosMessage msg2 = new PaxosMessage(buf);
		
		assertEquals(msg,msg2);
	}
	
	@Test
	public void testSerialisePropose() {
		PaxosMessage msg = new PaxosMessage(MessageFactory.PROPOSE, 0, 0, 0, TestHelper.createTestByte(), null);
		
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		PaxosMessage msg2 = new PaxosMessage(buf);
		
		assertEquals(msg,msg2);
	}
	@Test
	public void testSerialiseWeakStrongDecide() {
		PaxosMessage msg = new PaxosMessage(MessageFactory.WEAK, 0, 0, 0,TestHelper.createTestByte());
		
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		PaxosMessage msg2 = new PaxosMessage(buf);
		
		assertEquals(msg,msg2);
	}
	
	@Test
	public void testSerialiseCollectEmpty() {
		FreezeProof freeze = new FreezeProof(0, 1, 1, new byte[0], new byte[0], new byte[0]);
		PaxosMessage<CollectProof> msg = new PaxosMessage<CollectProof>(MessageFactory.COLLECT,0,0,0,new byte[0], new CollectProof(freeze, freeze, 1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		PaxosMessage<Proof> msg2 = new PaxosMessage<Proof>(buf);
		
		assertEquals(msg,msg2);
	}
	
	@Test
	public void testSerialiseCollectTestByte() {
		byte[] test = TestHelper.createTestByte();
		FreezeProof freeze = new FreezeProof(0, 1, 1, test, test, test);
		PaxosMessage<CollectProof> msg = new PaxosMessage<CollectProof>(MessageFactory.COLLECT,0,0,0,test, new CollectProof(freeze, freeze,1));
		msg.getProof().setSignature(TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		PaxosMessage<Proof> msg2 = new PaxosMessage<Proof>(buf);
		
		assertEquals(msg,msg2);
	}

}
