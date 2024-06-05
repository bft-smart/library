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
package bftsmart.tom.core;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.reconfiguration.ClientViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.KeyLoader;

import java.io.Closeable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is used to multicast messages to replicas and receive replies.
 */
public abstract class TOMSender implements ReplyReceiver, Closeable, AutoCloseable {

	private final int me; // process id
	private final ClientViewController viewController;
	private final int session; // session id
	private int sequence; // sequence number
	private int unorderedMessageSequence; // sequence number for readonly messages
	private final CommunicationSystemClientSide cs; // Client side communication system
	private final Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
	private final boolean useSignatures;
	private final AtomicInteger opCounter = new AtomicInteger(0);

	/**
	 * Creates a new instance of TOMulticastSender
	 * @param processId Process id for this client
	 * @param configHome Configuration directory for BFT-SMART
	 * @param loader Used to load signature keys from disk
	 */
	public TOMSender(int processId, String configHome, KeyLoader loader) {
		if (configHome == null) {
			this.viewController = new ClientViewController(processId, loader);
		} else {
			this.viewController = new ClientViewController(processId, configHome, loader);
		}
		this.me = this.viewController.getStaticConf().getProcessId();
		this.cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(processId, this.viewController);
		this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
		this.useSignatures = this.viewController.getStaticConf().getUseSignatures() == 1;
		this.session = new Random().nextInt();
	}

	public void close(){
		cs.close();
	}

	public CommunicationSystemClientSide getCommunicationSystem() {
		return this.cs;
	}

	public ClientViewController getViewManager(){
		return this.viewController;
	}

	public int getProcessId() {
		return me;
	}

	public int generateRequestId(TOMMessageType type) {
		lock.lock();
		int id;
		if(type == TOMMessageType.ORDERED_REQUEST || type == TOMMessageType.ORDERED_HASHED_REQUEST)
			id = sequence++;
		else
			id = unorderedMessageSequence++; 
		lock.unlock();

		return id;
	}

	public int generateOperationId() {
		return opCounter.getAndIncrement();
	}

	public void TOMulticast(TOMMessage sm) {
		cs.send(useSignatures, this.viewController.getCurrentViewProcesses(), sm);
	}

	public void TOMulticast(byte[] m, int reqId, int operationId, TOMMessageType reqType) {
		cs.send(useSignatures, viewController.getCurrentViewProcesses(),
				new TOMMessage(me, session, reqId, operationId, m, viewController.getCurrentViewId(),
						reqType));
	}

	public void sendMessageToTargets(byte[] m, int reqId, int operationId, int[] targets, TOMMessageType type) {
		if(this.getViewManager().getStaticConf().isTheTTP()) {
			type = TOMMessageType.ASK_STATUS;
		}
		cs.send(useSignatures, targets,
				new TOMMessage(me, session, reqId, operationId, m, viewController.getCurrentViewId(), type));
	}

	public int getSession(){
		return session;
	}

	/**
	 * Retrieves the required quorum size for the amount of replies
	 *
	 * @return The quorum size for the amount of replies
	 */
	public int getReplyQuorum() {
		return getViewManager().getReplyQuorum();
	}
}
