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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.reconfiguration.ClientViewController;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.KeyLoader;
import java.io.Closeable;
import java.security.Provider;

/**
 * This class is used to multicast messages to replicas and receive replies.
 */
public abstract class TOMSender implements ReplyReceiver, Closeable, AutoCloseable {

	private int me; // process id

	private ClientViewController viewController;

	private int session = 0; // session id
	private int sequence = 0; // sequence number
	private int unorderedMessageSequence = 0; // sequence number for readonly messages
	private CommunicationSystemClientSide cs; // Client side comunication system
	private Lock lock = new ReentrantLock(); // lock to manage concurrent access to this object by other threads
	private boolean useSignatures = false;
	private AtomicInteger opCounter = new AtomicInteger(0);

	/**
	 * Creates a new instance of TOMulticastSender
	 *
	 * TODO: This may really be empty?
	 */
	public TOMSender() {
	}

	public void close(){
		cs.close();
	}

	public CommunicationSystemClientSide getCommunicationSystem() {
		return this.cs;
	}


	//******* EDUARDO BEGIN **************//
	public ClientViewController getViewManager(){
		return this.viewController;
	}

	/**
	 * This method initializes the object
	 * TODO: Ask if this method cannot be protected (compiles, but....)
	 *
	 * @param processId ID of the process
	 */
	public void init(int processId, KeyLoader loader) {
		this.viewController = new ClientViewController(processId, loader);
		startsCS(processId);
	}

	public void init(int processId, String configHome, KeyLoader loader) {
		this.viewController = new ClientViewController(processId,configHome, loader);
		startsCS(processId);
	}

	private void startsCS(int clientId) {
		this.cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(clientId, this.viewController);
		this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
		this.me = this.viewController.getStaticConf().getProcessId();
		this.useSignatures = this.viewController.getStaticConf().getUseSignatures()==1?true:false;
		this.session = new Random().nextInt();
	}
	//******* EDUARDO END **************//


	public int getProcessId() {
		return me;
	}

	public int generateRequestId(TOMMessageType type) {
		lock.lock();
		int id;
		if(type == TOMMessageType.ORDERED_REQUEST)
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
}
