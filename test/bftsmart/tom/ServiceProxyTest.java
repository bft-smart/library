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
package bftsmart.tom;

import java.lang.reflect.Field;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.Test;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;

/**
 * 
 * @author Marcel Santos
 *
 */
public class ServiceProxyTest {
	
	private static ServiceProxy proxy;
	
	@BeforeClass
	public static void setup(){
		proxy = new ServiceProxy(1001);
	}
	
	@Test
	public void testReplyReceived() {
		// Just to setup some variables
		
		Field response;
		Field receivedReplies;
		Field reqId;
		Field replyQuorum;
		Field replies;
		Field requestType;
		
		try {
			response = ServiceProxy.class.getDeclaredField("response");
			response.setAccessible(true);
			receivedReplies = ServiceProxy.class.getDeclaredField("receivedReplies");
			receivedReplies.setAccessible(true);
			reqId = ServiceProxy.class.getDeclaredField("reqId");
			reqId.setAccessible(true);
			replyQuorum = ServiceProxy.class.getDeclaredField("replyQuorum");
			replyQuorum.setAccessible(true);
			replies = ServiceProxy.class.getDeclaredField("replies");
			replies.setAccessible(true);
			requestType = ServiceProxy.class.getDeclaredField("requestType");
			requestType.setAccessible(true);
			
			try {
				// Test if a the method decides correctly when three correct
				// replies are received in sequence. The request are ordered
		        TOMMessage[] initReplies = new TOMMessage[4];
				receivedReplies.set(proxy, 0);
				reqId.setInt(proxy, 1);
				replyQuorum.setInt(proxy, 3);
				replies.set(proxy, initReplies);
				requestType.set(proxy, TOMMessageType.ORDERED_REQUEST);
				response.set(proxy, null);

				TOMMessage msg = new TOMMessage(0, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(1, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(2, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				TOMMessage reply = (TOMMessage)response.get(proxy);
				
				Assert.assertEquals("response1", new String(reply.getContent()));

				// Test if a the method decides correctly when the replies are
				// correct, correct, wrong and correct, in that order. The requests
				// are ordered
		        initReplies = new TOMMessage[4];
				receivedReplies.set(proxy, 0);
				reqId.setInt(proxy, 1);
				replyQuorum.setInt(proxy, 3);
				replies.set(proxy, initReplies);
				requestType.set(proxy, TOMMessageType.ORDERED_REQUEST);
				response.set(proxy, null);

				msg = new TOMMessage(0, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(1, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(2, 1, 1, "response2".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(3, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				reply = (TOMMessage)response.get(proxy);
				
				Assert.assertEquals("response1", new String(reply.getContent()));
				
				// Negative test, to verify if the method doesn't decide when the values
				// doesn't match as a reply quorum. The values are 2 response1 and 2 response2
		        initReplies = new TOMMessage[4];
				receivedReplies.set(proxy, 0);
				reqId.setInt(proxy, 1);
				replyQuorum.setInt(proxy, 3);
				replies.set(proxy, initReplies);
				requestType.set(proxy, TOMMessageType.ORDERED_REQUEST);
				response.set(proxy, null);

				msg = new TOMMessage(0, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(1, 1, 1, "response1".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(2, 1, 1, "response2".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(3, 1, 1, "response2".getBytes(), 0, TOMMessageType.ORDERED_REQUEST);
				proxy.replyReceived(msg);
				reply = (TOMMessage)response.get(proxy);
				
				Assert.assertNull(reply);
				Assert.assertEquals(-1, reqId.get(proxy));

				// Test replyReceived for correct readonly messages
		        initReplies = new TOMMessage[4];
				receivedReplies.set(proxy, 0);
				reqId.setInt(proxy, 1);
				replies.set(proxy, initReplies);
				requestType.set(proxy, TOMMessageType.UNORDERED_REQUEST);
				response.set(proxy, null);

				msg = new TOMMessage(0, 1, 1, "response1".getBytes(), 0, TOMMessageType.UNORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(1, 1, 1, "response1".getBytes(), 0, TOMMessageType.UNORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(2, 1, 1, "response1".getBytes(), 0, TOMMessageType.UNORDERED_REQUEST);
				proxy.replyReceived(msg);
				
				reply = (TOMMessage)response.get(proxy);
				Assert.assertEquals("response1", new String(reply.getContent()));
				
				// Test if the method fails for the first diverging readonly reply
				receivedReplies.set(proxy, 0);
				reqId.setInt(proxy, 1);
				replies.set(proxy, initReplies);
				requestType.set(proxy, TOMMessageType.UNORDERED_REQUEST);
				response.set(proxy, null);

				msg = new TOMMessage(0, 1, 1, "response1".getBytes(), 0, TOMMessageType.UNORDERED_REQUEST);
				proxy.replyReceived(msg);
				msg = new TOMMessage(1, 1, 1, "response2".getBytes(), 0, TOMMessageType.UNORDERED_REQUEST);
				proxy.replyReceived(msg);
				
				reply = (TOMMessage)response.get(proxy);
				Assert.assertEquals(-1, reqId.get(proxy));
				
			} catch (IllegalArgumentException | IllegalAccessException e) {
				
				e.printStackTrace();
			}
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

}
