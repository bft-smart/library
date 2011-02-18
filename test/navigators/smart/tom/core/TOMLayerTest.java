/**
 * 
 */
package navigators.smart.tom.core;

import static org.junit.Assert.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.consensus.Consensus;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.statemanagment.SMMessage;
import navigators.smart.tests.util.TestHelper;
import navigators.smart.tom.TOMReceiver;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.BatchBuilder;
import navigators.smart.tom.util.TOMConfiguration;
import navigators.smart.tom.util.TOMUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * @author spann
 *
 */
public class TOMLayerTest {

	private TOMLayer tl;
	TOMReceiver recv;
	TOMConfiguration conf;
	ServerCommunicationSystem cs;
	
	/**
	 * @throws java.lang.Exception
	 */
	@Before
	public void setUp() throws Exception {
		recv = mock(TOMReceiver.class);
		cs = mock(ServerCommunicationSystem.class);
		conf  = new TOMConfiguration(0);
		tl = new TOMLayer(recv, cs, conf);
		
	}

	/**
	 * @throws java.lang.Exception
	 */
	@After
	public void tearDown() throws Exception {
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#computeHash(byte[])}.
	 */
	@Test
	public void testComputeHash() {
		byte[] testbyte = TestHelper.createTestByte();
		byte[] ret = tl.computeHash(testbyte);
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			assertArrayEquals(ret, md.digest(testbyte));
		} catch (NoSuchAlgorithmException e) {
			fail();
		}
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#requestReceived(navigators.smart.tom.core.messages.TOMMessage)}.
	 */
	@Test
	public void testRequestReceived() {
		TOMMessage msg = mock(TOMMessage.class);
		when(msg.isReadOnlyRequest()).thenReturn(true);
		tl.requestReceived(msg);
		verify(recv).receiveMessage(msg);
		msg = mock(TOMMessage.class);
		when(msg.isReadOnlyRequest()).thenReturn(false);
		ConsensusService srv = mock(ConsensusService.class);
		tl.setConsensusService(srv);
		tl.requestReceived(msg);
		verify(recv,never()).receiveMessage(msg);
		verify(srv).notifyNewRequest(msg);
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#createPropose()}.
	 */
	@Test
	public void testCreatePropose() {
		TOMMessage msg = new TOMMessage(0, 0, TestHelper.createTestByte());
		byte[][] msgs = new byte[1][];
		msgs[0] = msg.getBytes();
		ConsensusService srv = mock(ConsensusService.class);
		tl.setConsensusService(srv);
		tl.requestReceived(msg);
		verify(srv).notifyNewRequest(msg);
		byte[] prop = tl.createPropose();
		BatchBuilder bb = new BatchBuilder();
		byte[] test = bb.createBatch(0, 0, 1, msgs[0].length, msgs, null);
		assertTrue(test.length == prop.length);
		assertArrayEquals(Arrays.copyOfRange(test,16,test.length), Arrays.copyOfRange(prop,16,prop.length));
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#decided(navigators.smart.consensus.Consensus)}.
	 */
	@Test
	public void testDecided() {
		TOMMessage msg = new TOMMessage(0, 0, TestHelper.createTestByte());
		TOMMessage[] array = new TOMMessage[1];
		array[0] = msg;
		Consensus<TOMMessage[]> cons = new Consensus<TOMMessage[]>(0);
		cons.setDeserialisedDecision(array);
		ConsensusService srv = mock(ConsensusService.class);
		tl.setConsensusService(srv);
		tl.requestReceived(msg);
		tl.decided(cons);
		verify(srv).notifyNewRequest(msg);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		verify(srv).notifyRequestDecided(msg);
		verify(recv).receiveOrderedMessage(msg);
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#checkProposedValue(byte[])}.
	 */
	@Test
	public void testCheckProposedValue() {
		TOMMessage msg = new TOMMessage(0, 0, TestHelper.createTestByte());
		byte[][] msgs = new byte[1][];
		TOMMessage[] array = new TOMMessage[1];
		array[0] = msg;
		msgs[0] = msg.getBytes();
		Consensus<TOMMessage[]> cons = new Consensus<TOMMessage[]>(0);
		cons.setDeserialisedDecision(array);
		ConsensusService srv = mock(ConsensusService.class);
		tl.setConsensusService(srv);
		BatchBuilder bb = new BatchBuilder();
		byte[] test = bb.createBatch(0, 0, 1, msgs[0].length, msgs, null);
		assertArrayEquals(array, tl.checkProposedValue(test));
	}

	
	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#requestStateTransfer(int, int[], int, long)}.
	 */
	@Test
	public void testRequestStateTransfer() {
		int[] otherAcceptors = new int[2];
		otherAcceptors[0]=0;
		otherAcceptors[1]=1;
		tl.requestStateTransfer(2, otherAcceptors, 0, 0);
		verify(cs,never()).send(otherAcceptors, new SMMessage(2, 0, TOMUtil.SM_REQUEST, 0, null));
		tl.requestStateTransfer(2, otherAcceptors, 1, 0);
		verify(cs).send(otherAcceptors, new SMMessage(2, 0, TOMUtil.SM_REQUEST, 0, null));
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#SMRequestDeliver(navigators.smart.statemanagment.SMMessage)}.
	 */
	@Test
	public void testSMRequestDeliver() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#SMReplyDeliver(navigators.smart.statemanagment.SMMessage)}.
	 */
	@Test
	public void testSMReplyDeliver() {
		fail("Not yet implemented");
	}

	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#isRetrievingState()}.
	 */
	@Test
	public void testIsRetrievingState() {
		assertFalse(tl.isRetrievingState());
		int[] otherAcceptors = new int[2];
		otherAcceptors[0]=0;
		otherAcceptors[1]=1;
		tl.requestStateTransfer(2, otherAcceptors, 0, 1);
		tl.requestStateTransfer(2, otherAcceptors, 1, 1);
		assertTrue(tl.isRetrievingState());
	}


	/**
	 * Test method for {@link navigators.smart.tom.core.TOMLayer#hasPendingRequests()}.
	 */
	@Test
	public void testHasPendingRequests() {
		assertFalse(tl.hasPendingRequests());
		TOMMessage msg = mock(TOMMessage.class);
		when(msg.isReadOnlyRequest()).thenReturn(false);
		when(msg.getBytes()).thenReturn(TestHelper.createTestByte());
		ConsensusService srv = mock(ConsensusService.class);
		tl.setConsensusService(srv);
		tl.requestReceived(msg);
		assertTrue(tl.hasPendingRequests());
		tl.createPropose();
		assertFalse(tl.hasPendingRequests());
		
	}

}
