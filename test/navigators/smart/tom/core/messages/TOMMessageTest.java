package navigators.smart.tom.core.messages;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import navigators.smart.tests.util.TestHelper;

import org.junit.Test;

public class TOMMessageTest {
	
	@Test
	public void testMsgSize(){
		TOMMessage msg = new TOMMessage(1, 3, TestHelper.createTestByte());
		ByteBuffer buf = ByteBuffer.allocate(1024);
		msg.serialise(buf);
		assert(msg.getMsgSize()==buf.position());
	}

	@Test
	public void testSerialise() {
		TOMMessage msg = new TOMMessage(1, 3, TestHelper.createTestByte());
		
		ByteBuffer buf = ByteBuffer.allocate(msg.getMsgSize());
		
		msg.serialise(buf);
		
		buf.rewind();
		
		TOMMessage msg2 = new TOMMessage(buf);
		
		assertEquals(msg,msg2);
		
	}

	@Test
	public void testCompareTo() {
		TOMMessage msg = new TOMMessage(1, 1, TestHelper.createTestByte());
		TOMMessage msg2 = new TOMMessage(1, 2, TestHelper.createTestByte());
		
		assert(msg.compareTo(msg2)<0);
		assert(msg2.compareTo(msg)>0);
		assert(msg.compareTo(msg)==0);
	}

}
