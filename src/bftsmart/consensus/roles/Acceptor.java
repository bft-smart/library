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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

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
	// private Cipher cipher;
	private Mac mac;

	/**
	 * Tulio Ribeiro
	 */
	private BlockingQueue<ConsensusMessage> insertProof;
	private BlockingQueue<ConsensusMessage> readProof;
	private PrivateKey privKey;
	private boolean hasProof;
	private boolean hasReconf;
	private String proofType;

	/**
	 * Creates a new instance of Acceptor.
	 * 
	 * @param communication
	 *            Replicas communication system
	 * @param factory
	 *            Message factory for PaW messages
	 * @param controller
	 */
	public Acceptor(ServerCommunicationSystem communication, MessageFactory factory, ServerViewController controller) {
		this.communication = communication;
		this.me = controller.getStaticConf().getProcessId();
		this.factory = factory;
		this.controller = controller;

		/* Tulio Ribeiro */
		this.hasProof  = false;
		this.hasReconf = false;
		this.privKey = controller.getStaticConf().getPrivateKey();
		this.insertProof = new LinkedBlockingDeque<>();
		this.readProof = new LinkedBlockingDeque<>();
		InsertProofThread ipt = new InsertProofThread(this.insertProof, this.controller);
		new Thread(ipt).start();
		this.proofType = controller.getStaticConf().getProofType();

		try {
			this.mac = TOMUtil.getMacFactory();
			logger.debug("Setting MAC with TOMUtil.getMacFactory(). ReplicaId: {}", me);
		} catch (NoSuchAlgorithmException /* | NoSuchPaddingException */ ex) {
			logger.error("Failed to get MAC engine", ex);
		}

	}

	public MessageFactory getFactory() {
		return factory;
	}

	/**
	 * Sets the execution manager for this acceptor
	 * 
	 * @param manager
	 *            Execution manager for this acceptor
	 */
	public void setExecutionManager(ExecutionManager manager) {
		this.executionManager = manager;
	}

	/**
	 * Sets the TOM layer for this acceptor
	 * 
	 * @param tom
	 *            TOM layer for this acceptor
	 */
	public void setTOMLayer(TOMLayer tom) {
		this.tomLayer = tom;
	}

	/**
	 * Called by communication layer to delivery Paxos messages. This method only
	 * verifies if the message can be executed and calls process message (storing it
	 * on an out of context message buffer if this is not the case)
	 *
	 * @param msg
	 *            Paxos messages delivered by the communication layer
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
	 * @param msg
	 *            The message to be processed
	 */
	public final void processMessage(ConsensusMessage msg) {
		Consensus consensus = executionManager.getConsensus(msg.getNumber());

		consensus.lock.lock();
		Epoch epoch = consensus.getEpoch(msg.getEpoch(), controller);
		switch (msg.getType()) {
		case MessageFactory.PROPOSE: {
			/*
			 * logger.trace("Epoch on (RECEIVED PROPOSE): {}", epoch.toString()); logger.
			 * debug("Size of Consensus Message (PROPOSE), before process it. Size:{}.",
			 * sizeCM(msg));
			 */
			proposeReceived(epoch, msg);
		}
			break;
		case MessageFactory.WRITE: {
			/*
			 * logger.trace("Epoch on (RECEIVED WRITE): {}", epoch.toString());
			 * logger.debug("Size of Consensus Message (WRITE), before process it. Size:{}."
			 * , sizeCM(msg));
			 */
			writeReceived(epoch, msg.getSender(), msg.getValue());
		}
			break;
		case MessageFactory.ACCEPT: {
			/*
			 * logger.trace("Epoch on (RECEIVED ACCEPT): {}", epoch.toString()); logger.
			 * trace("Size of Consensus Message (ACCEPT), before process it. Size:{}.",
			 * sizeCM(msg));
			 */
			acceptReceived(epoch, msg);
		}
		}
		consensus.lock.unlock();
	}

	/**
	 * Called when a PROPOSE message is received or when processing a formerly out
	 * of context propose which is know belongs to the current consensus.
	 *
	 * @param msg
	 *            The PROPOSE message to by processed
	 */
	public void proposeReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		int ts = epoch.getConsensus().getEts();
		int ets = executionManager.getConsensus(msg.getNumber()).getEts();
		logger.debug("PROPOSE for consensus " + cid);
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
	 * @param epoch
	 *            the current epoch of the consensus
	 * @param value
	 *            Value that is proposed
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
			logger.debug("I have written value " + Arrays.toString(epoch.propValueHash) + " in consensus instance "
					+ cid + " with timestamp " + epoch.getConsensus().getEts());
			/*****************************************/

			// start this consensus if it is not already running
			if (cid == tomLayer.getLastExec() + 1) {
				tomLayer.setInExec(cid);
			}
			epoch.deserializedPropValue = tomLayer.checkProposedValue(value, true);

			if (epoch.deserializedPropValue != null && !epoch.isWriteSetted(me)) {
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
					communication.send(this.controller.getCurrentViewOtherAcceptors(),
							factory.createWrite(cid, epoch.getTimestamp(), epoch.propValueHash));

					logger.debug("WRITE sent for " + cid);

					computeWrite(cid, epoch, epoch.propValueHash);

					logger.debug("WRITE computed for " + cid);

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

					computeAccept(cid, epoch, epoch.propValueHash);
				}
				executionManager.processOutOfContext(epoch.getConsensus());

			} else if (epoch.deserializedPropValue == null && !tomLayer.isChangingLeader()) { // force a leader change
																								// if the proposal is
																								// garbage

				tomLayer.getSynchronizer().triggerTimeout(new LinkedList<>());
			}
		}
	}

	/**
	 * Called when a WRITE message is received
	 *
	 * @param epoch
	 *            Epoch of the receives message
	 * @param a
	 *            Replica that sent the message
	 * @param value
	 *            Value sent in the message
	 */
	private void writeReceived(Epoch epoch, int a, byte[] value) {
		int cid = epoch.getConsensus().getId();
		logger.debug("WRITE from " + a + " for consensus " + cid);
		epoch.setWrite(a, value);

		computeWrite(cid, epoch, value);
	}

	/**
	 * Computes WRITE values according to Byzantine consensus specification values
	 * received).
	 *
	 * @param cid
	 *            Consensus ID of the received message
	 * @param epoch
	 *            Epoch of the receives message
	 * @param value
	 *            Value sent in the message
	 */
	private void computeWrite(int cid, Epoch epoch, byte[] value) {
		int writeAccepted = epoch.countWrite(value);

		logger.debug("I have {}, WRITE's for cId:{}, Epoch timestamp:{},", writeAccepted, cid, epoch.getTimestamp());

		if (writeAccepted > controller.getQuorum() && Arrays.equals(value, epoch.propValueHash)) {

			if (!epoch.isAcceptSetted(me)) {

				logger.debug("Sending ACCEPT for " + cid);

				/**** LEADER CHANGE CODE! ******/
				logger.debug("Setting consensus " + cid + " QuorumWrite tiemstamp to " + epoch.getConsensus().getEts()
						+ " and value " + Arrays.toString(value));
				epoch.getConsensus().setQuorumWrites(value);
				/*****************************************/

				epoch.setAccept(me, value);

				if (epoch.getConsensus().getDecision().firstMessageProposed != null) {
					epoch.getConsensus().getDecision().firstMessageProposed.acceptSentTime = System.nanoTime();
				}

				// insertProof(cm, epoch);
				ConsensusMessage cm = null;
				try {
					if (this.hasProof) {
						logger.debug("Waiting for readProof Blocking Queue... cID: {}", cid);
						cm = readProof.take();
					} else {
						// Deal with some case where the protocol does not execute from begin, as leader
						// change.
						logger.info("Proof not done yes, leader change?, hasProof:{}", this.hasProof);
						advanceInsertProof(cid, epoch.getTimestamp(), value, epoch.deserializedPropValue);
						cm = readProof.take();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				int[] targets = this.controller.getCurrentViewOtherAcceptors();
				communication.getServersConn().send(targets, cm, true);

				epoch.addToProof(cm);
				computeAccept(cid, epoch, value);
			}
		} else if (!hasProof) {
			advanceInsertProof(cid, epoch.getTimestamp(), value, epoch.deserializedPropValue);
		}
	}

	/**
	 * Advancing signature proof for Accept message. It is called by Synchornizer at
	 * Leader Changes.
	 * 
	 * @param cid:
	 *            consensus id
	 * @param epoch:
	 *            epoch
	 * @param value:
	 *            value sent in the message
	 */
	public void advanceInsertProof(int cid, int epochTimestamp, byte[] value,  TOMMessage[] msgs) {

		// check if consensus contains reconfiguration request
		if (!proofType.equalsIgnoreCase("signatures")) {

			for (TOMMessage msg : msgs) {
				if (msg.getReqType() == TOMMessageType.RECONFIG && msg.getViewID() == controller.getCurrentViewId()) {
					hasReconf = true;
					break; // no need to continue, exit the loop
				}
			}
		}

		hasProof = true;
		logger.debug("Advancing signature for ACCEPT message. cId:{}", cid);
		ConsensusMessage cm = factory.createAccept(cid, epochTimestamp, value);
		try {
			insertProof.put(cm);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Create a cryptographic proof for a consensus message
	 * 
	 * This method modifies the consensus message passed as an argument, so that it
	 * contains a cryptographic proof.
	 * 
	 * @param cm
	 *            The consensus message to which the proof shall be set
	 * @param epoch
	 *            The epoch during in which the consensus message was created
	 */
	/*
	 * private void insertProof(ConsensusMessage cm, Epoch epoch) {
	 * ByteArrayOutputStream bOut = new ByteArrayOutputStream(248); try { new
	 * ObjectOutputStream(bOut).writeObject(cm); } catch (IOException ex) {
	 * logger.error("Failed to serialize consensus message",ex); }
	 * 
	 * byte[] data = bOut.toByteArray();
	 * 
	 * // check if consensus contains reconfiguration request TOMMessage[] msgs =
	 * epoch.deserializedPropValue; boolean hasReconf = false;
	 * 
	 * for (TOMMessage msg : msgs) { if (msg.getReqType() == TOMMessageType.RECONFIG
	 * && msg.getViewID() == controller.getCurrentViewId()) { hasReconf = true;
	 * break; // no need to continue, exit the loop } }
	 * 
	 * //If this consensus contains a reconfiguration request, we need to use //
	 * signatures (there might be replicas that will not be part of the next
	 * //consensus instance, and so their MAC will be outdated and useless) if
	 * (hasReconf) {
	 * 
	 * byte[] signature = TOMUtil.signMessage(privKey, data);
	 * 
	 * cm.setProof(signature);
	 * 
	 * } else { //... if not, we can use MAC vectors int[] processes =
	 * this.controller.getCurrentViewAcceptors();
	 * 
	 * HashMap<Integer, byte[]> macVector = new HashMap<>();
	 * 
	 * //logger.
	 * trace("Size of Consensus Message (ACCEPT), before insertProof. Size:{}, Macs:{}"
	 * , sizeCM(cm), macVector.size());
	 * 
	 * for (int id : processes) {
	 * 
	 * try {
	 * 
	 * SecretKey key = null; do { key = communication.getSecretKey(id); if (key ==
	 * null) { logger.warn("I don't have yet a secret key with " + id +
	 * ". Retrying."); Thread.sleep(1000); }
	 * 
	 * } while (key == null); // JCS: This loop is to solve a race condition where a
	 * // replica might have already been inserted in the view or // recovered after
	 * a crash, but it still did not concluded // the Diffie-Hellman protocol. Not
	 * an elegant solution, // but for now it will do this.mac.init(key);
	 * macVector.put(id, this.mac.doFinal(data)); } catch (InterruptedException ex)
	 * { logger.error("Interruption while sleeping", ex); } catch
	 * (InvalidKeyException ex) {
	 * 
	 * logger.error("Failed to generate MAC vector", ex); } }
	 * 
	 * cm.setProof(macVector); //logger.
	 * trace("Size of Consensus Message (ACCEPT), after insertProof. Size:{}, Macs:{}"
	 * , sizeCM(cm), macVector.size()); }
	 * 
	 * }
	 */

	/**
	 * Called when a ACCEPT message is received
	 * 
	 * @param epoch
	 *            Epoch of the receives message
	 * @param a
	 *            Replica that sent the message
	 * @param value
	 *            Value sent in the message
	 */
	private void acceptReceived(Epoch epoch, ConsensusMessage msg) {
		int cid = epoch.getConsensus().getId();
		epoch.setAccept(msg.getSender(), msg.getValue());
		epoch.addToProof(msg);
		logger.debug("ACCEPT received from replica:{}, for consensus cId:{}.", msg.getSender(), cid);

		computeAccept(cid, epoch, msg.getValue());
	}

	/**
	 * Computes ACCEPT values according to the Byzantine consensus specification
	 * 
	 * @param epoch
	 *            Epoch of the receives message
	 * @param value
	 *            Value sent in the message
	 */
	private void computeAccept(int cid, Epoch epoch, byte[] value) {
		logger.debug("I have {} ACCEPTs for cId:{}, Timestamp:{} ", epoch.countAccept(value), cid,
				epoch.getTimestamp());

		if (epoch.countAccept(value) > controller.getQuorum() && !epoch.getConsensus().isDecided()) {
			logger.debug("Deciding consensus " + cid);
			hasProof = false;
			hasReconf = false;
			decide(epoch);
		}
	}

	/**
	 * This is the method invoked when a value is decided by this process
	 * 
	 * @param epoch
	 *            Epoch at which the decision is made
	 */
	private void decide(Epoch epoch) {
		if (epoch.getConsensus().getDecision().firstMessageProposed != null)
			epoch.getConsensus().getDecision().firstMessageProposed.decisionTime = System.nanoTime();

		epoch.getConsensus().decided(epoch, true);
	}

	/**
	 * Create a cryptographic proof for a consensus message Thread used to advance
	 * the signature process.
	 * 
	 * The proof is inserted into a Blocking Queue read by ComputeWrite.
	 */

	private class InsertProofThread implements Runnable {
		private BlockingQueue<ConsensusMessage> insertProof;

		public InsertProofThread(BlockingQueue<ConsensusMessage> queue, ServerViewController controller) {
			insertProof = queue;
		}

		@Override
		public void run() {
			logger.info("Advanced proof thread running. ThreadId: {}", Thread.currentThread().getId());
			while (true) {
				try {
					ConsensusMessage cm = insertProof.take();

					ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
					try {
						new ObjectOutputStream(bOut).writeObject(cm);
					} catch (IOException ex) {
						logger.error("Failed to serialize consensus message", ex);
					}
					byte[] data = bOut.toByteArray();

					
					if(hasReconf || proofType.equalsIgnoreCase("signatures")) { 
						//If we have a reconfiguration, we need to sign.
						//Sign the message. 
						byte[] signature = TOMUtil.signMessage(privKey, data);
						cm.setProof(signature);
					}
					else if(proofType.equalsIgnoreCase("macVector")){//... otherwise, we will use MAC vectors
				            
				            Mac mac = null;				            
				            try {				            
				                mac = TOMUtil.getMacFactory();				            
				            } catch (NoSuchAlgorithmException ex) {
				                logger.error("Failed to create MAC engine", ex);
				                return;
				            }
				            
				            int[] processes = controller.getCurrentViewAcceptors();

				            HashMap<Integer, byte[]> macVector = new HashMap<>();

				            for (int id : processes) {

				                try {

				                    SecretKey key = null;
				                    do {
				                        key = communication.getServersConn().getSecretKey(id);
				                        if (key == null) {
				                            logger.warn("I don't have yet a secret key with " + id + ". Retrying.");
				                            Thread.sleep(1000);
				                        }

				                    } while (key == null);  // JCS: This loop is to solve a race condition where a
				                                            // replica might have already been inserted in the view or
				                                            // recovered after a crash, but it still did not concluded
				                                            // the diffie helman protocol. Not an elegant solution,
				                                            // but for now it will do
				                    mac.init(key);
				                    macVector.put(id, mac.doFinal(data));
				                } catch (InterruptedException ex) {
				                    
				                    logger.error("Interruption while sleeping", ex);
				                } catch (InvalidKeyException ex) {

				                    logger.error("Failed to generate MAC vector", ex);
				                }
				            }

				            cm.setProof(macVector);
					}
					
					readProof.put(cm);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * private int sizeCM(ConsensusMessage cm) { ByteArrayOutputStream bOut2 = new
	 * ByteArrayOutputStream(248); try { new
	 * ObjectOutputStream(bOut2).writeObject(cm); } catch (IOException ex) {
	 * logger.error("Failed to serialize consensus message", ex); } byte[] data2 =
	 * bOut2.toByteArray();
	 * 
	 * return data2.length;
	 * 
	 * }
	 */
}
