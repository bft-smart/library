package bftsmart.reconfiguration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import bftsmart.TestFixture;

public class TTPTest extends TestFixture {
	
	@Test
	public void testAskStatus() {
		TTP ttp = new TTP();
		try{
			Thread.sleep(2000);
			StatusReply status = ttp.askStatus(1);
			assertEquals("Status should be READY", StatusReply.READY, status);
			
			stopServer(1);
			Thread.sleep(2000);
			status = ttp.askStatus(1);
			assertEquals("Status should be OFFLINE", StatusReply.OFFLINE, status);
			
			
		} catch(InterruptedException ie) {
			System.out.println("Exception during Thread sleep: " + ie.getMessage());
		}
	}
}
