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

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.communication.client.CommunicationSystemClientSide;
import bftsmart.communication.client.CommunicationSystemClientSideFactory;
import bftsmart.communication.client.ReplyReceiver;
import bftsmart.reconfiguration.ClientViewManager;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;

/**
 * This class is used to multicast messages to replicas and receive replies.
 */
public abstract class TOMSender implements ReplyReceiver {

	private int me; // process id

	private ClientViewManager viewManager;

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
	public ClientViewManager getViewManager(){
		return this.viewManager;
	}

	/**
	 * This method initializes the object
	 * TODO: Ask if this method cannot be protected (compiles, but....)
	 *
	 * @param processId ID of the process
	 */
	public void init(int processId) {
		this.viewManager = new ClientViewManager(processId);
		startsCS();
	}

	public void init(int processId, String configHome) {
		this.viewManager = new ClientViewManager(processId,configHome);
		startsCS();
	}

	private void startsCS() {
		this.cs = CommunicationSystemClientSideFactory.getCommunicationSystemClientSide(this.viewManager);
		this.cs.setReplyReceiver(this); // This object itself shall be a reply receiver
		this.me = this.viewManager.getStaticConf().getProcessId();
		this.useSignatures = this.viewManager.getStaticConf().getUseSignatures()==1?true:false;
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

	//******* EDUARDO BEGIN **************//
	/**
	 * Multicast data to the group of replicas
	 *
	 * @param m Data to be multicast
	 */
	//public void TOMulticast(byte[] m) {
	//    TOMulticast(new TOMMessage(me, session, generateRequestId(), m,
	//            this.viewManager.getCurrentViewId()));
	//}

	/**
	 * Multicast a TOMMessage to the group of replicas
	 *
	 * @param sm Message to be multicast
	 */
	public void TOMulticast(TOMMessage sm) {
		cs.send(useSignatures, this.viewManager.getCurrentViewProcesses(), sm);
	}

	/**
	 * Multicast data to the group of replicas
	 *
	 * @param m Data to be multicast
	 * @param reqId unique integer that identifies this request
	 * @param reqType TOM_NORMAL, TOM_READONLY or TOM_RECONFIGURATION
	 */
	public void TOMulticast(byte[] m, int reqId, TOMMessageType reqType) {
		cs.send(useSignatures, viewManager.getCurrentViewProcesses(),
				new TOMMessage(me, session, reqId, m, viewManager.getCurrentViewId(),
						reqType));
	}

	public void TOMulticast(byte[] m, int reqId, int operationId, TOMMessageType reqType) {
		cs.send(useSignatures, viewManager.getCurrentViewProcesses(),
				new TOMMessage(me, session, reqId, operationId, m, viewManager.getCurrentViewId(),
						reqType));
	}

	public void sendMessageToTargets(byte[] m, int reqId, int[] targets, TOMMessageType type) {
		if(this.getViewManager().getStaticConf().isTheTTP()) {
			type = TOMMessageType.ASK_STATUS;
		}
		cs.send(useSignatures, targets,
				new TOMMessage(me, session, reqId, m, viewManager.getCurrentViewId(), type));
	}

	public void sendMessageToTargets(byte[] m, int reqId, int operationId, int[] targets, TOMMessageType type) {
		if(this.getViewManager().getStaticConf().isTheTTP()) {
			type = TOMMessageType.ASK_STATUS;
		}
		cs.send(useSignatures, targets,
				new TOMMessage(me, session, reqId, operationId, m, viewManager.getCurrentViewId(), type));
	}

	/**
	 * Create TOMMessage and sign it
	 *
	 * @param m Data to be included in TOMMessage
	 *
	 * @return TOMMessage with serializedMsg and serializedMsgSignature fields filled
	 */
	//public TOMMessage sign(byte[] m) {
	//    TOMMessage tm = new TOMMessage(me, session, generateRequestId(), m,
	//           this.viewManager.getCurrentViewId());
	//    cs.sign(tm);
	//    return tm;
	//}

	//******* EDUARDO END **************//
}
