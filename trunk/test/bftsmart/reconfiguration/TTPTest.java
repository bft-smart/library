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
package bftsmart.reconfiguration;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import bftsmart.TestFixture;

/**
 * 
 * @author Marcel Santos
 *
 */
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
