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
import java.security.PublicKey;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import bftsmart.consensus.Decision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.consensus.Consensus;
import bftsmart.consensus.Epoch;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
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

	/**
	 * Creates a new instance of Acceptor.
	 * 
	 * @param communication Replicas communication system
	 * @param factory       Message factory for PaW messages
	 * @param controller sever view controller
	 */
	public Acceptor(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller) {
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
		if (executionManager.checkLimits(msg)) {
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
			writeReceived(epoch, msg.getSender(), msg.getValue());
		}
			break;
		case MessageFactory.ACCEPT: {
			acceptReceived(epoch, msg);
		}
			break;

		// START DECISION FORWARDING
		case MessageFactory.REQ_DECISION: {
			if (controller.getStaticConf().useReadOnlyRequests())
				requestDecisionReceived(epoch, msg);
		}
			break;
		case MessageFactory.FWD_DECISION: {
			if (controller.getStaticConf().useReadOnlyRequests())
				forwardDecisionReceived(epoch, msg);
		}
		// END DECISION FORWARDING

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

					logger.debug("Sending WRITE for cId:{}, I am:{}", cid, me);
					communication.send(this.controller.getCurrentViewOtherAcceptors(),
							factory.createWrite(cid, epoch.getTimestamp(), epoch.propValueHash));

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
	 * @param sender     Replica that sent the message
	 * @param value Value sent in the message
	 */
	private void writeReceived(Epoch epoch, int sender, byte[] value) {
		int cid = epoch.getConsensus().getId();
		logger.debug("WRITE received from:{}, for consensus cId:{}", 
				sender, cid);
		epoch.setWrite(sender, value);

		computeWrite(cid, epoch, value);
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
	 * @param cm   The consensus message to which the proof shall be set
	 * @param msgs tom messages
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
	 * @param msg Consenus Message
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
	 * @param cid id of consensus
	 * @param epoch Epoch of the receives message
	 * @param value Value sent in the message
	 */
	private void computeAccept(int cid, Epoch epoch, byte[] value) {
		logger.debug("I have {} ACCEPTs for cId:{}, Timestamp:{} ", epoch.countAccept(value), cid,
				epoch.getTimestamp());

		if (epoch.countAccept(value) > controller.getQuorum()
				&& !epoch.getConsensus().isDecided()
				&& Arrays.equals(value, epoch.propValueHash)) {
			logger.debug("Deciding consensus " + cid);
			decide(epoch);

			// START DECISION_FORWARDING
			if (controller.getStaticConf().useReadOnlyRequests())
				forwardDecision(epoch);
			// END DECISION_FORWARDING
		}
	}

	private void forwardDecision(Epoch epoch) {
		int cid = epoch.getConsensus().getId();

		// the "toForward" set defines replicas we remembered to forward the decision to
		// these are the replicas from which we received a "REQ-DECISION" message, i.e. a request to forward the decision
		int[] targets = executionManager.getToForward(cid);

		logger.debug("Forwarding Decision necessary? for cid " + cid);

		if (targets != null &&
				executionManager.getCurrentLeader() != controller.getStaticConf().getProcessId()) {  // Forwarding is necessary if toForward set is non-empty (non null)

			// Create the "FORWARD-DECISION" message
			byte[] value = epoch.getConsensus().getDecision().getValue();
			ConsensusMessage forwardDecision = factory.createForwardDecision(cid, epoch.getTimestamp(), value);
			forwardDecision.setProof(epoch.getProof());

			logger.debug("Attaching proof for forwarded decision: " + cid + " | " + value + " | " + forwardDecision.getProof());

			// Forward to all targets.
			communication.send(targets, forwardDecision);
		} else {
			logger.debug("No replicas in toForward set" + cid);
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


	private void broadcastDecision(Epoch epoch) {
		int cid = epoch.getConsensus().getId();
		logger.debug("Forwarding Decision for cid " + cid);

		// Create the "FORWARD-DECISION" message
		byte[] value = epoch.getConsensus().getDecision().getValue();
		ConsensusMessage forwardDecision = factory.createForwardDecision(cid, epoch.getTimestamp(), value);
		forwardDecision.setProof(epoch.getProof());

		logger.debug("Attaching proof for forwarded decision: " + cid + " | " + value + " | " + forwardDecision.getProof());

		communication.send(controller.getReplicasWithout(controller.getCurrentViewOtherAcceptors(),
				executionManager.getCurrentLeader()), forwardDecision);
	}


	/**
	 * Send a REQ_DECISION to others
	 *
	 * @param epoch the current epoch
	 * @param cid consensus id
	 * @param receivers the replicas that are asked
	 * @param value epoch.propValueHash
	 */
	public void sendRequestDecision(Epoch epoch, int cid, int[] receivers, byte[] value) {
		communication.send(receivers, factory.createRequestDecision(cid, epoch.getTimestamp(), value));
	}

	/**
	 * Called when a REQUEST_DECISION message is received
	 *
	 * @param epoch Epoch of the receives message
	 * @param msg the message
	 */
	private void requestDecisionReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();

		logger.debug(">>>>>>> Received REQ_DECISION from " + msg.getSender()  + " for consensus " + cid);

		// Check if consensus cid is already decided
		// if it is, we can directly forward the decision to the requester
		if (epoch.getConsensus().isDecided()) {
			logger.debug(">>> >> >>  > Consensus " + cid  + " is already decided ");
			Decision decision = epoch.getConsensus().getDecision();

			// Dont forward a decision twice for the same requester in the same consensus instance
			if ( !executionManager.hasBeenForwardedAlready(msg.getEpoch(), msg.getSender())) {
				logger.debug(">>> >> >> >> > Send FWD_DECISION for epoch " + epoch.getTimestamp() + " to replica " + msg.getSender());

				byte[] value = decision.getValue();
				int[] targets = new int[1];
				targets[0] = msg.getSender();

				// Create and send a FWD-Decision message
				ConsensusMessage forwardDecision = factory.createForwardDecision(cid, epoch.getTimestamp(), value);
				forwardDecision.setProof(epoch.getProof());
				communication.send(targets, forwardDecision);
				executionManager.addForwarded(msg.getNumber(), msg.getSender());
			}
		} else {
			boolean consensusIsDecidedButForgotten = msg.getNumber() <= tomLayer.getLastExec() - controller.getStaticConf().getCheckpointPeriod();
			if (consensusIsDecidedButForgotten) {
				// we will also arrive here if a replica forgets about past consensues, because the are removed from the consensuses map
				// this means the requester is left far behind and needs to perform a state transfer to catch up

				logger.warn("decision request is too old to handle (decision has been garbage collected) and will be ignored");
			} else {
				logger.debug(">>> >> >>  > Consensus " + cid  +
						" is still undecided remembering replica " + msg.getSender() + " to be forwarded to after deciding");

				// Consensus still undecided, remember to forward decision
				logger.debug("Remember cid for addToForward " + msg.getEpoch());
				executionManager.addToForward(msg.getNumber(), msg.getSender());
			}
		}
	}

	/**
	 * Called when a FORWARD_DECISION message is received
	 *
	 * @param epoch Epoch of the receives message
	 * @param msg the message
	 */
	private void forwardDecisionReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		logger.debug(">>>>>>> Received FWD_DECISION from " + msg.getSender()  + " for consensus " + cid);

		// Use proof to Check if decision is valid
		boolean decisionIsValid = verifyDecision(msg);

		// Still undecided and decision is valid and can be accepted, then
		if (!epoch.getConsensus().isDecided() && decisionIsValid) {

			logger.debug("Deciding consensus " + cid + " using the forwarded decision!");
			// If decision is valid, set deserializedPropValue in epoch
			epoch.deserializedPropValue = tomLayer.checkProposedValue(msg.getValue(), true);

			// Attach decision to epoch
			Decision decision = epoch.getConsensus().getDecision();
			decision.setValue(msg.getValue());
			decision.setDecisionEpoch(epoch);

			// Attach proof to epoch
			epoch.setProof((HashSet<ConsensusMessage>) msg.getProof());

			// Decide the epoch!
			decide(epoch);

			// broadcast the decision to ensure correctness
			broadcastDecision(epoch);
		}
	}


	/**
	 * verifyDecision takes a FORWARDED-DECISION message and checks if the contained decision can be verified using
	 * the attached proof. The proof is a set of ACCEPT messages which
	 *
	 * @param msg a Consensus Message: must be a FORWARD-DECISION
	 * @return if the FORWARD-DECISION is good
	 */
	public boolean verifyDecision(ConsensusMessage msg) {
		HashSet<ConsensusMessage> proof = (HashSet<ConsensusMessage>) msg.getProof();
		if (proof == null) {
			logger.warn("ACCEPTOR.verifyDecision: Received proof is NULL");
		} else {
			logger.debug("ACCEPTOR.verifyDecision: Received Proof for forwarded decision: " + msg.getNumber() + " | " + msg.getValue() + " | " + proof);

			byte[] decisionHash = tomLayer.computeHash(msg.getValue());
			int numberOfValidAccepts = 0;
			HashSet<Integer> replicaID_already_counted = new HashSet<>();

			// For each ACCEPT message contained in the proof, check if the signature is correct
			for (ConsensusMessage accept : proof) {

				ConsensusMessage cm = new ConsensusMessage(accept.getType(), accept.getNumber(), accept.getEpoch(),
						 accept.getSender(), accept.getValue());

				ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
				try {
					new ObjectOutputStream(bOut).writeObject(cm);
				} catch (IOException ex) {
					logger.error("ACCEPTOR.verifyDecision: Could not serialize message", ex);
				}

				byte[] data = bOut.toByteArray();
				byte[] signature = (byte[]) accept.getProof();

				logger.debug("ACCEPTOR.verifyDecision: Proof made of Signatures");

				// Verify the signature of the ACCEPT
				PublicKey pubKey = controller.getStaticConf().getPublicKey(accept.getSender());
				boolean validSignature = TOMUtil.verifySignature(pubKey, data, signature);

				if (!validSignature) {
					logger.warn("ACCEPTOR.verifyDecision:  Signature is invalid!");
				}

				// The ACCEPT is valid and will be counted iff
				if (Arrays.equals(accept.getValue(), decisionHash)    // decision hash equals digest in ACCEPT
						&& validSignature 							  // ACCEPT's signature was successfully verified
						&& !replicaID_already_counted.contains(accept.getSender())) { // unique: a replica may vote only once!

					replicaID_already_counted.add(accept.getSender());
					numberOfValidAccepts++;
				}
			}

			// A quorum certificate of valid ACCEPTs makes a decision valid
			boolean decisionIsValid = numberOfValidAccepts > controller.getQuorum();
			if(!decisionIsValid) {
				logger.warn("ACCEPTOR.verifyDecision: Too few signed accepts received; # " + numberOfValidAccepts + " " + proof);
			}
			return  decisionIsValid;
		}
		return false;
	}

}
