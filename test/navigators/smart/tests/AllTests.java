package navigators.smart.tests;

import navigators.smart.communication.server.TestSerialization;
import navigators.smart.paxosatwar.executionmanager.ExecutionManagerTest;
import navigators.smart.paxosatwar.messages.PaxosMessageTest;
import navigators.smart.tom.core.TOMLayerTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;


@RunWith(Suite.class)
@SuiteClasses( { 
	TestSerialization.class,
	ExecutionManagerTest.class,
	PaxosMessageTest.class,
	TOMLayerTest.class})


public class AllTests {

}
