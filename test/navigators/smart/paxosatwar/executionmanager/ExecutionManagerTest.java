package navigators.smart.paxosatwar.executionmanager;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import navigators.smart.consensus.Consensus;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaxosMessage;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.statemanagment.TransferableState;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.util.TOMConfiguration;

import org.junit.Before;
import org.junit.Test;

public class ExecutionManagerTest {
	
	private ExecutionManager mng;
	Acceptor acceptor;
	Proposer proposer ;
	int[] acceptors = {0,1,2,3};
	int f;
	int me;
	long initialTimeout;
	TOMLayer tom ;
	LeaderModule lm;
	RequestHandler handlr;
	
	@Before
	public void setUp(){
		acceptor = mock(Acceptor.class);
		proposer = mock(Proposer.class);
		f = 1;
		me = 0;
		initialTimeout = 600000;
		tom = mock(TOMLayer.class);
		lm = mock(LeaderModule.class);
		handlr = mock(RequestHandler.class);
		TOMConfiguration conf = mock(TOMConfiguration.class);
		when(tom.getConf()).thenReturn(conf);
		when(conf.getPaxosHighMark()).thenReturn(100);
		when(conf.getRevivalHighMark()).thenReturn(10);
		mng = new ExecutionManager(acceptor, proposer, acceptors, f, me, initialTimeout, tom, lm);
		mng.setRequestHandler(handlr);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCheckLimits_initial() {
		int[] others = {1,2,3}; //list of the other acceptors
		
		//test initial configuration
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertTrue(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 0, 0, 1)));
		assertFalse(mng.thereArePendentMessages(0));
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		//test initial ooc message with state transfer
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 99, 0, 1)));
		verify(tom).requestStateTransfer(me, others, 1, 99);
		assertTrue(mng.thereArePendentMessages(1));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testCheckLimits_normal() {
		int[] others = {1,2,3}; //list of the other acceptors
		//test normal configuration
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertTrue(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertFalse(mng.thereArePendentMessages(1));
		//test normal execution ooc msg
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 2, 0, 1)));
		assertTrue(mng.thereArePendentMessages(2));
		//test normal execution ooc msg with state transfer
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 101, 0, 1)));
		verify(tom).requestStateTransfer(me, others, 1, 101);
		assertTrue(mng.thereArePendentMessages(101l));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testCheckLimits_initial_StateTransfer() {
		//STATE TRANSFER ENABLED
		//test initial configuration
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 0, 0, 1)));
		assertTrue(mng.thereArePendentMessages(0));
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		//test initial ooc message with state transfer
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 99, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));		
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCheckLimits_normal_StateTransfer() {
		//STATE TRANSFER ENABLED
		
		//test normal configuration
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		//test normal execution ooc msg
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 2, 0, 1)));
		assertTrue(mng.thereArePendentMessages(2));
		//test normal execution ooc msg with state transfer
		when(handlr.getLastExec()).thenReturn(0l);
		when(handlr.getInExec()).thenReturn(1l);
		when(tom.isRetrievingState()).thenReturn(true);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 101, 0, 1)));
		assertTrue(mng.thereArePendentMessages(101l));
	}
	
	@Test
	public void testThereArePendentMessages() {
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.WEAK, 2, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2));
	}

	@Test
	public void testRemoveExecution() {
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		mng.removeExecution(1);
		assertFalse(mng.thereArePendentMessages(1));
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.WEAK, 2, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2));
		mng.removeExecution(2);
		assertFalse(mng.thereArePendentMessages(2));
	}

	@Test
	public void testRemoveOutOfContexts() {
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.WEAK, 2, 0, 1, new byte[0])));
		assertTrue(mng.thereArePendentMessages(2));
		mng.removeOutOfContexts(0);
		assertTrue(mng.thereArePendentMessages(1));
		assertTrue(mng.thereArePendentMessages(2));
		mng.removeOutOfContexts(1);
		assertFalse(mng.thereArePendentMessages(1));
		assertTrue(mng.thereArePendentMessages(2));
		mng.removeOutOfContexts(2);
		assertFalse(mng.thereArePendentMessages(2));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testGetExecution() {
		Execution exec = mng.getExecution(0);
		assertEquals(exec, mng.removeExecution(exec.getId()));
		
		//test initial ooc message
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		PaxosMessage msg = new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1);
		PaxosMessage weak = new PaxosMessage(MessageFactory.WEAK, 1, 0, 1,new byte[0]);
		assertFalse(mng.checkLimits(msg));
		assertFalse(mng.checkLimits(weak));
		exec = mng.getExecution(1);
		verify(acceptor).processMessage(msg);
		verify(acceptor).processMessage(weak);
		assertEquals(exec, mng.removeExecution(exec.getId()));
	}

	@Test
	public void testDecided() {
		mng.getExecution(0);
		mng.decided(new Consensus(0));
		verify(handlr).setLastExec(0);
		verify(acceptor).executeAcceptedPendent(1);

		//verify with removal of stable consensus
		mng.getExecution(0);
		mng.decided(new Consensus(3));
		verify(handlr,times(2)).setInExec(-1); //verify  both resets of inExec
		verify(handlr).setLastExec(3);
		verify(lm).removeStableConsenusInfos(0);
		assertNull(mng.removeExecution(0));
		verify(acceptor).executeAcceptedPendent(4);
	}

	@Test
	public void testDeliverState() {
		when(handlr.getLastExec()).thenReturn(-1l);
		when(handlr.getInExec()).thenReturn(-1l);
		when(tom.isRetrievingState()).thenReturn(false);
		assertFalse(mng.checkLimits(new PaxosMessage(MessageFactory.PROPOSE, 1, 0, 1)));
		assertTrue(mng.thereArePendentMessages(1));
		
		TransferableState state = mock(TransferableState.class);
		mng.getExecution(5);
		when(state.getLastEid()).thenReturn(10l);
		mng.deliverState(state);
		verify(handlr).setLastExec(10);
		verify(handlr).setNoExec();
		assertNull(mng.removeExecution(5));
		assertFalse(mng.thereArePendentMessages(1));
	}

}
