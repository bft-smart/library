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
package bftsmart.consensus.roles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.Consensus;
import bftsmart.consensus.Epoch;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.forensic.AuditResult;
import bftsmart.forensic.AuditStorage;
import bftsmart.forensic.Auditor;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.TOMUtil;

/**
 * This class represents the acceptor role in the consensus protocol. This class
 * work together with the TOMLayer class in order to supply a atomic multicast
 * service.
 *
 * @author Alysson Bessani
 */
public final class Acceptor {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private int me; // This replica ID
	private ExecutionManager executionManager; // Execution manager of consensus's executions
	private MessageFactory factory; // Factory for PaW messages
	private ServerCommunicationSystem communication; // Replicas comunication system
	private TOMLayer tomLayer; // TOM layer
	private ServerViewController controller;

	// thread pool used to paralelise creation of consensus proofs
	private ExecutorService proofExecutor = null;

	/**
	 * Tulio Ribeiro
	 */
	private PrivateKey privKey;

	// Forensics
	private AuditStorage storage;
	private Auditor audit;
	private int lastAudit = -1;
	Map<Integer, Integer> nAudits = new HashMap<>(); // consensus id to number of storages receives (usefull for garbage
														// collection)

	public Acceptor(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller) {
		this(communication, factory, controller, null);
	}

	/**
	 * Creates a new instance of Acceptor.
	 * 
	 * @param communication Replicas communication system
	 * @param factory       Message factory for PaW messages
	 * @param controller
	 */
	public Acceptor(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller,
			AuditStorage storage) {
		this.storage = storage;
		this.audit = new Auditor();

		this.communication = communication;
		this.me = controller.getStaticConf().getProcessId();
		this.factory = factory;
		this.controller = controller;

		/* Tulio Ribeiro */
		this.privKey = controller.getStaticConf().getPrivateKey();

		// use either the same number of Netty workers threads if specified in the
		// configuration
		// or use a many as the number of cores available
		/*
		 * int nWorkers = this.controller.getStaticConf().getNumNettyWorkers(); nWorkers
		 * = nWorkers > 0 ? nWorkers : Runtime.getRuntime().availableProcessors();
		 * this.proofExecutor = Executors.newWorkStealingPool(nWorkers);
		 */
		this.proofExecutor = Executors.newSingleThreadExecutor();
	}

	public MessageFactory getFactory() {
		return factory;
	}

	/**
	 * Sets the execution manager for this acceptor
	 * 
	 * @param manager Execution manager for this acceptor
	 */
	public void setExecutionManager(ExecutionManager manager) {
		this.executionManager = manager;
	}

	/**
	 * Sets the TOM layer for this acceptor
	 * 
	 * @param tom TOM layer for this acceptor
	 */
	public void setTOMLayer(TOMLayer tom) {
		this.tomLayer = tom;
	}

	/**
	 * Called by communication layer to delivery Paxos messages. This method only
	 * verifies if the message can be executed and calls process message (storing it
	 * on an out of context message buffer if this is not the case)
	 *
	 * @param msg Paxos messages delivered by the communication layer
	 */
	public final void deliver(ConsensusMessage msg) {
		if (msg.getType() == MessageFactory.AUDIT || msg.getType() == MessageFactory.STORAGE
				|| executionManager.checkLimits(msg)) {
			logger.debug("Processing paxos msg with id " + msg.getNumber());
			processMessage(msg);
		} else {
			logger.debug("Out of context msg with id " + msg.getNumber());
			tomLayer.processOutOfContext();
		}
	}

	/**
	 * Called when a Consensus message is received or when a out of context message
	 * must be processed. It processes the received message according to its type
	 *
	 * @param msg The message to be processed
	 */
	public final void processMessage(ConsensusMessage msg) {
		Consensus consensus = executionManager.getConsensus(msg.getNumber());

		consensus.lock.lock();
		Epoch epoch = consensus.getEpoch(msg.getEpoch(), controller);
		switch (msg.getType()) {
			case MessageFactory.PROPOSE: {
				proposeReceived(epoch, msg);
			}
				break;
			case MessageFactory.WRITE: {
				writeReceived(epoch, msg.getSender(), msg);
			}
				break;
			case MessageFactory.ACCEPT: {
				acceptReceived(epoch, msg);
			}
				break;
			case MessageFactory.AUDIT: {
				auditReceived(epoch, msg);
			}
				break;
			case MessageFactory.STORAGE: {
				storageReceived(msg);
			}
				break;
		}
		consensus.lock.unlock();
	}

	/**
	 * Called when a PROPOSE message is received or when processing a formerly out
	 * of context propose which is know belongs to the current consensus.
	 *
	 * @param msg The PROPOSE message to by processed
	 */
	public void proposeReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		int ts = epoch.getConsensus().getEts();
		int ets = executionManager.getConsensus(msg.getNumber()).getEts();
		logger.debug("PROPOSE received from:{}, for consensus cId:{}, I am:{}", msg.getSender(), cid, me);
		if (msg.getSender() == executionManager.getCurrentLeader() // Is the replica the leader?
				&& epoch.getTimestamp() == 0 && ts == ets && ets == 0) { // Is all this in epoch 0?
			executePropose(epoch, msg.getValue());
		} else {
			logger.debug("Propose received is not from the expected leader");
		}
	}

	/**
	 * Executes actions related to a proposed value.
	 *
	 * @param epoch the current epoch of the consensus
	 * @param value Value that is proposed
	 */
	private void executePropose(Epoch epoch, byte[] value) {
		int cid = epoch.getConsensus().getId();
		logger.debug("Executing propose for cId:{}, Epoch Timestamp:{}", cid, epoch.getTimestamp());

		long consensusStartTime = System.nanoTime();

		if (epoch.propValue == null) { // only accept one propose per epoch
			epoch.propValue = value;
			epoch.propValueHash = tomLayer.computeHash(value);

			/*** LEADER CHANGE CODE ********/
			epoch.getConsensus().addWritten(value);
			logger.trace("I have written value " + Arrays.toString(epoch.propValueHash) + " in consensus instance "
					+ cid + " with timestamp " + epoch.getConsensus().getEts());
			/*****************************************/

			// start this consensus if it is not already running
			if (cid == tomLayer.getLastExec() + 1) {
				tomLayer.setInExec(cid);
			}
			epoch.deserializedPropValue = tomLayer.checkProposedValue(value, true);

			if (epoch.deserializedPropValue != null && !epoch.isWriteSent()) {
				if (epoch.getConsensus().getDecision().firstMessageProposed == null) {
					epoch.getConsensus().getDecision().firstMessageProposed = epoch.deserializedPropValue[0];
				}
				if (epoch.getConsensus().getDecision().firstMessageProposed.consensusStartTime == 0) {
					epoch.getConsensus().getDecision().firstMessageProposed.consensusStartTime = consensusStartTime;

				}
				epoch.getConsensus().getDecision().firstMessageProposed.proposeReceivedTime = System.nanoTime();

				if (controller.getStaticConf().isBFT()) {
					logger.debug("Sending WRITE for " + cid);

					epoch.setWrite(me, epoch.propValueHash);
					epoch.getConsensus().getDecision().firstMessageProposed.writeSentTime = System.nanoTime();

					/**
					 * Forensics
					 */
					ConsensusMessage writeMessage = factory.createWrite(cid, epoch.getTimestamp(), epoch.propValueHash);
					insertProof(writeMessage, epoch.deserializedPropValue); // insert proof in write message
					epoch.addWriteProof(writeMessage);
					/**
					 * 
					 */

					logger.debug("Sending WRITE for cId:{}, I am:{}", cid, me);
					communication.send(this.controller.getCurrentViewOtherAcceptors(), writeMessage);

					epoch.writeSent();

					computeWrite(cid, epoch, epoch.propValueHash);

					logger.debug("WRITE computed for cId:{}, I am:{}", cid, me);

				} else {
					epoch.setAccept(me, epoch.propValueHash);
					epoch.getConsensus().getDecision().firstMessageProposed.writeSentTime = System.nanoTime();
					epoch.getConsensus().getDecision().firstMessageProposed.acceptSentTime = System.nanoTime();

					/**** LEADER CHANGE CODE! ******/
					logger.debug("[CFT Mode] Setting consensus " + cid + " QuorumWrite tiemstamp to "
							+ epoch.getConsensus().getEts() + " and value " + Arrays.toString(epoch.propValueHash));
					epoch.getConsensus().setQuorumWrites(epoch.propValueHash);
					/*****************************************/

					communication.send(this.controller.getCurrentViewOtherAcceptors(),
							factory.createAccept(cid, epoch.getTimestamp(), epoch.propValueHash));

					epoch.acceptSent();
					computeAccept(cid, epoch, epoch.propValueHash);
				}
				executionManager.processOutOfContext(epoch.getConsensus());

			} else if (epoch.deserializedPropValue == null
					&& !tomLayer.isChangingLeader()) { // force a leader change
				tomLayer.getSynchronizer().triggerTimeout(new LinkedList<>());
			}
		}
	}

	/**
	 * Called when a WRITE message is received
	 *
	 * @param epoch Epoch of the receives message
	 * @param a     Replica that sent the message
	 * @param value Value sent in the message
	 */
	private void writeReceived(Epoch epoch, int sender, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		logger.debug("WRITE received from:{}, for consensus cId:{}",
				sender, cid);
		epoch.setWrite(sender, msg.getValue());

		/**
		 * Forensics
		 */
		epoch.addWriteProof(msg);
		/**
		 * 
		 */

		computeWrite(cid, epoch, msg.getValue());
	}

	/**
	 * Computes WRITE values according to Byzantine consensus specification values
	 * received).
	 *
	 * @param cid   Consensus ID of the received message
	 * @param epoch Epoch of the receives message
	 * @param value Value sent in the message
	 */
	private void computeWrite(int cid, Epoch epoch, byte[] value) {
		int writeAccepted = epoch.countWrite(value);

		logger.debug("I have {}, WRITE's for cId:{}, Epoch timestamp:{},", writeAccepted, cid, epoch.getTimestamp());

		if (writeAccepted > controller.getQuorum()
				&& Arrays.equals(value, epoch.propValueHash)) {

			if (!epoch.isAcceptSent()) {

				/**
				 * Forensics
				 * Who partecipated in write quorum
				 */
				if (storage != null) {
					epoch.createWriteAggregate();
					storage.addWriteAggregate(cid, epoch.getWriteAggregate());
				}
				/**
				 * 
				 */

				logger.debug("Sending ACCEPT message, cId:{}, I am:{}", cid, me);

				/**** LEADER CHANGE CODE! ******/
				logger.debug("Setting consensus " + cid + " QuorumWrite tiemstamp to " + epoch.getConsensus().getEts()
						+ " and value " + Arrays.toString(value));
				epoch.getConsensus().setQuorumWrites(value);
				/*****************************************/

				if (epoch.getConsensus().getDecision().firstMessageProposed != null) {

					epoch.getConsensus().getDecision().firstMessageProposed.acceptSentTime = System.nanoTime();
				}

				ConsensusMessage cm = epoch.fetchAccept();
				int[] targets = this.controller.getCurrentViewAcceptors();
				epoch.acceptSent();

				if (Arrays.equals(cm.getValue(), value)) { // make sure the ACCEPT message generated upon receiving the
															// PROPOSE message
															// still matches the value that ended up being written...

					logger.debug(
							"Speculative ACCEPT message for consensus {} matches the written value, sending it to the other replicas",
							cid);

					communication.getServersConn().send(targets, cm, true);

				} else { // ... and if not, create the ACCEPT message again (with the correct value), and
							// send it

					ConsensusMessage correctAccept = factory.createAccept(cid, epoch.getTimestamp(), value);

					proofExecutor.submit(() -> {

						// Create a cryptographic proof for this ACCEPT message
						logger.debug(
								"Creating cryptographic proof for the correct ACCEPT message from consensus " + cid);
						insertProof(correctAccept, epoch.deserializedPropValue);

						communication.getServersConn().send(targets, correctAccept, true);

					});
				}

			}

		} else if (!epoch.isAcceptCreated()) { // start creating the ACCEPT message and its respective proof ASAP, to
												// increase performance.
												// since this is done after a PROPOSE message is received, this is done
												// speculatively, hence
												// the value must be verified before sending the ACCEPT message to the
												// other replicas

			ConsensusMessage cm = factory.createAccept(cid, epoch.getTimestamp(), value);
			epoch.acceptCreated();

			proofExecutor.submit(() -> {

				// Create a cryptographic proof for this ACCEPT message
				logger.debug("Creating cryptographic proof for speculative ACCEPT message from consensus " + cid);
				insertProof(cm, epoch.deserializedPropValue);

				epoch.setAcceptMsg(cm);

			});
		}
	}

	/**
	 * Create a cryptographic proof for a consensus message
	 * 
	 * This method modifies the consensus message passed as an argument, so that it
	 * contains a cryptographic proof.
	 * 
	 * @param cm    The consensus message to which the proof shall be set
	 * @param epoch The epoch during in which the consensus message was created
	 */
	private void insertProof(ConsensusMessage cm, TOMMessage[] msgs) {
		ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
		try {
			ObjectOutputStream obj = new ObjectOutputStream(bOut);
			obj.writeObject(cm);
			obj.flush();
			bOut.flush();
		} catch (IOException ex) {
			logger.error("Failed to serialize consensus message", ex);
		}

		byte[] data = bOut.toByteArray();

		// Always sign a consensus proof.
		byte[] signature = TOMUtil.signMessage(privKey, data);

		cm.setProof(signature);

	}

	/**
	 * Called when a ACCEPT message is received
	 * 
	 * @param epoch Epoch of the receives message
	 * @param a     Replica that sent the message
	 * @param value Value sent in the message
	 */
	private void acceptReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		logger.debug("ACCEPT from " + msg.getSender() + " for consensus " + cid);
		epoch.setAccept(msg.getSender(), msg.getValue());
		epoch.addToProof(msg);

		computeAccept(cid, epoch, msg.getValue());
	}

	/**
	 * Computes ACCEPT values according to the Byzantine consensus specification
	 * 
	 * @param epoch Epoch of the receives message
	 * @param value Value sent in the message
	 */
	private void computeAccept(int cid, Epoch epoch, byte[] value) {
		logger.debug("I have {} ACCEPTs for cId:{}, Timestamp:{} ", epoch.countAccept(value), cid,
				epoch.getTimestamp());

		if (epoch.countAccept(value) > controller.getQuorum() && !epoch.getConsensus().isDecided()) {
			logger.debug("Deciding consensus " + cid);

			/**
			 * Forensics
			 * who partecipated in accept quorum
			 */
			if (storage != null) {
				epoch.createAcceptAggregate();
				storage.addAcceptAggregate(cid, epoch.getAcceptAggregate());
			}
			/**
			 * 
			 */

			decide(epoch);

			// Forensics
			// placeholder for testing, should be modified to make all replicas audit from
			// time to time
			// in different cids
			// if (me == 0) {
			// System.out.println("### Starting Forensics ###");
			// sendAudit(cid, epoch);
			// }
		}
	}

	/**
	 * This is the method invoked when a value is decided by this process
	 * 
	 * @param epoch Epoch at which the decision is made
	 */
	private void decide(Epoch epoch) {
		if (epoch.getConsensus().getDecision().firstMessageProposed != null)
			epoch.getConsensus().getDecision().firstMessageProposed.decisionTime = System.nanoTime();

		epoch.getConsensus().decided(epoch, true);
	}

	/*************************** FORENSICS METHODS *******************************/

	public AuditStorage getAudiStorage() {
		return storage;
	}

	/**
	 * Called when audit sender is client
	 * Receives audit message and sends storage to client
	 * 
	 * @param msg message received
	 */
	public void auditReceived(TOMMessage msg) {
		System.out.println("Audit message received from " + msg.getSender());
		TOMMessage response = new TOMMessage(me, msg.getSession(), msg.getSequence(),
				msg.getOperationId(), this.storage.toByteArray(), msg.getViewID(), TOMMessageType.AUDIT);
		communication.getClientsConn().send(new int[] { msg.getSender() }, response, false); // send to storage to
																								// sender client
	}

	/**
	 * Called when audit sender is replica
	 * Receives audit message and sends storage to sender replica
	 * 
	 * @param epoch consensus epoch
	 * @param msg   consensus message
	 */
	private void auditReceived(Epoch epoch, ConsensusMessage msg) {
		System.out.println("Audit message received from " + msg.getSender());
		ConsensusMessage cm = factory.createStorage(epoch.getConsensus().getId(), epoch.getTimestamp(),
				storage.toByteArray());
		System.out.println("Will send storage to " + msg.getSender());
		communication.getServersConn().send(new int[] { msg.getSender() }, cm, true); // send storage to sender replica
	}

	/**
	 * Sends audit message to all replicas
	 * 
	 * @param cid   consensus id
	 * @param epoch consensus epoch
	 */
	private void sendAudit(int cid, Epoch epoch) {
		int[] targets = this.controller.getCurrentViewOtherAcceptors();
		ConsensusMessage cm = this.factory.createAudit(cid, epoch.getTimestamp());
		communication.getServersConn().send(targets, cm, true);
	}

	/**
	 * Called when storage message is received
	 * Receives storage message and executes forensics
	 * prints conflict if found
	 * 
	 * @param msg consensus message received
	 */
	private void storageReceived(ConsensusMessage msg) {
		System.out.println("Storage message received from " + msg.getSender());
		AuditStorage receivedStorage = AuditStorage.fromByteArray(msg.getValue());
		int minCid = receivedStorage.getMinCID();
		int maxCid = receivedStorage.getMaxCID();

		if (maxCid <= lastAudit) {
			return; // received storage does not have needed information...
		}
		AuditResult result = audit.audit(this.storage, receivedStorage, lastAudit + 1);

		if (result.conflictFound()) {
			System.out.println(result);
			// TODO inform other replicas <PANIC MESSAGE>
		} else {
			System.out.println("No conflict found");
		}

		for (int i = minCid; i < maxCid; i++) { // update information on audits executed
			if (nAudits.containsKey(i)) {
				int reps = nAudits.get(i);
				nAudits.put(i, reps + 1);
			} else {
				nAudits.put(i, 1);
			}
			if (nAudits.get(i) >= 2 * controller.getCurrentViewF() + 1) {
				lastAudit = i; // this is the last cid that for certain is safe
				storage.removeProofsUntil(i); // garbage collection for unecessary proofs
			}
		}
		// TODO when to remove from nAudits Map?
	}
}
