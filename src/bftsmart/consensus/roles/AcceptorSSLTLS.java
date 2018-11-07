/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, Tulio Ribeiro and the authors indicated in the @author tags

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

/**
 * TODO
 * Remove / clear readProof blocking queue in case of non decided consensus. 
 * */

package bftsmart.consensus.roles;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.communication.ServerCommunicationSystem.ConnType;
import bftsmart.consensus.Consensus;
import bftsmart.consensus.Epoch;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.reconfiguration.ServerViewController;
import bftsmart.tom.core.ExecutionManager;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.BatchReader;
import bftsmart.tom.util.TOMUtil;

/**
 * This class represents the acceptor role in the consensus protocol. This class
 * work together with the TOMLayer class in order to supply a atomic multicast
 * service.
 *
 * @author Alysson Bessani
 */
public final class AcceptorSSLTLS {

	private Logger logger = LoggerFactory.getLogger(this.getClass());

	private int me; // This replica ID
	private ExecutionManager executionManager; // Execution manager of consensus's executions
	private MessageFactory factory; // Factory for PaW messages
	private ServerCommunicationSystem communication; // Replicas comunication system
	private TOMLayer tomLayer; // TOM layer
	private ServerViewController controller;

	/**
	 * Tulio Ribeiro
	 */
	private BlockingQueue<ConsensusMessage> insertProof;
	private BlockingQueue<ConsensusMessage> readProof;
	private PrivateKey privKey;
	private boolean hasProof;

	//Disk
	private BlockingQueue<HashMap<Integer, byte[]>> toPersistBatch;
	private BlockingQueue<HashMap<Integer, Set<ConsensusMessage>>> toPersistProof;
	private BlockingQueue<Integer> saved;
	private Boolean isPersistent = false;
	private String storeDataDir;
	
	
	/**
	 * Creates a new instance of Acceptor.
	 * 
	 * @param communication
	 *            Replicas communication system
	 * @param factory
	 *            Message factory for PaW messages
	 * @param controller
	 */
	public AcceptorSSLTLS(ServerCommunicationSystem communication, MessageFactory factory,
			ServerViewController controller) {
		this.communication = communication;
		this.me = controller.getStaticConf().getProcessId();
		this.factory = factory;
		this.controller = controller;

		/* Tulio Ribeiro */
		this.hasProof = false;
		this.privKey = controller.getStaticConf().getPrivateKey();
		this.insertProof = new LinkedBlockingDeque<>();
		this.readProof = new LinkedBlockingDeque<>();
		InsertProofThread ipt = new InsertProofThread(this.insertProof);
		new Thread(ipt).start();
		
		// Deal with disk
		this.saved = new LinkedBlockingDeque<>();
		this.toPersistBatch = new LinkedBlockingDeque<>();
		this.toPersistProof = new LinkedBlockingDeque<>();
		
		SaveBatchToDisk dwd = new SaveBatchToDisk(this.toPersistBatch);
		new Thread(dwd).start();
		SaveProofToDisk spd = new SaveProofToDisk(this.toPersistProof);
		new Thread(spd).start();
		
		this.isPersistent = controller.getStaticConf().isPersistent();
		this.storeDataDir = controller.getStaticConf().getStoreDataDir() + "/replica_"+this.me;
		if (this.isPersistent) {
			File f = new File(this.storeDataDir);
			f.mkdirs();
			
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
			logger.trace("I have written value " + Arrays.toString(epoch.propValueHash) + " in consensus instance "
					+ cid + " with timestamp " + epoch.getConsensus().getEts());
			/*****************************************/

			// start this consensus if it is not already running
			if (cid == tomLayer.getLastExec() + 1) {
				tomLayer.setInExec(cid);
			}
			epoch.deserializedPropValue = tomLayer.checkProposedValue(value, true);

			if (epoch.deserializedPropValue != null && !epoch.isWriteSetted(me)) {
				
				
				if (this.isPersistent) {
					try {
						HashMap<Integer, byte[]> map = new HashMap<>();
						map.put(cid, value);
						toPersistBatch.put(map);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
				if (epoch.getConsensus().getDecision().firstMessageProposed == null) {
					epoch.getConsensus().getDecision().firstMessageProposed = epoch.deserializedPropValue[0];
				}
				if (epoch.getConsensus().getDecision().firstMessageProposed.consensusStartTime == 0) {
					epoch.getConsensus().getDecision().firstMessageProposed.consensusStartTime = consensusStartTime;

				}

				epoch.getConsensus().getDecision().firstMessageProposed.proposeReceivedTime = System.nanoTime();

				if (controller.getStaticConf().isBFT()) {
					logger.debug("Sending WRITE for cId:{}, I am:{}", cid, me);

					epoch.setWrite(me, epoch.propValueHash);
					epoch.getConsensus().getDecision().firstMessageProposed.writeSentTime = System.nanoTime();

					communication.send(this.controller.getCurrentViewOtherAcceptors(),
							factory.createWrite(cid, epoch.getTimestamp(), epoch.propValueHash));

					logger.debug("WRITE sent for cId:{}, I am:{}", cid, me);

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
	private void writeReceived(Epoch epoch, int sender, byte[] value) {
		int cid = epoch.getConsensus().getId();
		logger.debug("WRITE received from:{}, for consensus cId:{}", sender, cid);
		epoch.setWrite(sender, value);

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

				logger.debug("Sending ACCEPT message, cId:{}, I am:{}", cid, me);

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
					cm = readProof.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				int[] targets = controller.getCurrentViewOtherAcceptors();

				if (communication.getConnType().equals(ConnType.SSL_TLS))
					communication.getServersConnSSLTLS().send(targets, cm);
				else
					communication.getServersConn().send(targets, cm, true);

				epoch.addToProof(cm);
				computeAccept(cid, epoch, value);

			}
		} else if (!hasProof) {
			hasProof = true;
			logger.debug("Not into quorum yet, advancing Signature stuff. cId:{}", cid);
			ConsensusMessage cm = factory.createAccept(cid, epoch.getTimestamp(), value);
			try {
				insertProof.put(cm);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
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
	 * logger.error("Failed to serialize consensus message", ex); } byte[] data =
	 * bOut.toByteArray();
	 * 
	 * PrivateKey privKey = controller.getStaticConf().getPrivateKey(); byte[]
	 * signature = TOMUtil.signMessage(privKey, data); cm.setProof(signature); }
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
			
			if (this.isPersistent) {
				try {
					HashMap<Integer, Set<ConsensusMessage>> proof = new HashMap<>();
					proof.put(cid, epoch.getProof());
					this.toPersistProof.put(proof);
					saved.take();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
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

	/*
	 * private int sizeCM(ConsensusMessage cm) { ByteArrayOutputStream bOut2 = new
	 * ByteArrayOutputStream(248); try { new
	 * ObjectOutputStream(bOut2).writeObject(cm); } catch (IOException ex) {
	 * logger.error("Failed to serialize consensus message", ex); } byte[] data2 =
	 * bOut2.toByteArray();
	 * 
	 * return data2.length; }
	 */

	/**
	 * Create a cryptographic proof for a consensus message Thread used to advance
	 * the signature process. The proof is inserted into a Blocking Queue read by
	 * ComputeWrite.
	 */
	private class InsertProofThread implements Runnable {
		private BlockingQueue<ConsensusMessage> insertProof;
		public InsertProofThread(BlockingQueue<ConsensusMessage> queue) {
			insertProof = queue;
		}
		@Override
		public void run() {
			logger.debug("Insert proof thread running. ThreadId: {}", Thread.currentThread().getId());
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

					byte[] signature = TOMUtil.signMessage(privKey, data);

					cm.setProof(signature);

					readProof.put(cm);

				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	
	/**
	 * 
	 */
	private class SaveBatchToDisk implements Runnable {
		private BlockingQueue<HashMap<Integer, byte[]>> toPersist;

		public SaveBatchToDisk(BlockingQueue<HashMap<Integer, byte[]>> queue) {
			toPersist = queue;
		}
		@Override
		public void run() {
			logger.debug("Disk thread running. ThreadId: {}", Thread.currentThread().getId());
			RandomAccessFile raf = null;			
			//FileOutputStream fos = null;
			FileDescriptor fd = null;
			
			while (true) {
				try {										
					HashMap<Integer, byte[]> mapConsensus = toPersist.take();					
					Iterator<Integer> it = mapConsensus.keySet().iterator();
					while (it.hasNext()) {
						Integer cId = (Integer) it.next();
						String consensusFile = storeDataDir + "/cId_"+cId;
								
						try {
							raf = new RandomAccessFile(consensusFile, "rws");
							fd = raf.getFD();
							raf.write(mapConsensus.get(cId));
							fd.sync();
							raf.close();
							saved.put(cId);
						} catch (Exception e) {
							e.printStackTrace();
						}
						/*try {
							fos = new FileOutputStream(consensusFile , false);
							fd = fos.getFD();
							fos.write(mapConsensus.get(cId));
							fos.flush();
							fd.sync();
							fos.close();
							saved.put(cId);
						} catch (Exception e) {
							e.printStackTrace();
						} */

						if (logger.isTraceEnabled()) {
							// Informative code, read and show the saved batch.
							logger.info("READING BATCH FROM DISK, cId:{}", cId);
							try {
								byte[] data = Files.readAllBytes(new File(storeDataDir + "/cId_" + cId).toPath());
								BatchReader batchReader = new BatchReader(data,
										controller.getStaticConf().getUseSignatures());
								TOMMessage[] requests = null;
								requests = batchReader.deserialiseRequests(controller);
								for (TOMMessage request : requests) {
									logger.info("Request, Sender:{}, Req Type:{}", request.getSender(),
											request.getReqType());
								}
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private class SaveProofToDisk implements Runnable {
		private BlockingQueue<HashMap<Integer, Set<ConsensusMessage>>> toPersistProof;

		public SaveProofToDisk(BlockingQueue<HashMap<Integer, Set<ConsensusMessage>>> queue) {
			toPersistProof = queue;
		}

		@Override
		public void run() {
			logger.debug("Disk proof thread running. ThreadId: {}", Thread.currentThread().getId());
			
			FileOutputStream fos = null;
			RandomAccessFile raf = null;
			FileDescriptor fd = null;
			
			while (true) {
				try {
					
					HashMap<Integer, Set<ConsensusMessage>> mapProofs = toPersistProof.take();
					
					Iterator<Integer> it = mapProofs.keySet().iterator();
					while (it.hasNext()) {
						Integer cId = (Integer) it.next();
						Set<ConsensusMessage> proofsToSave = mapProofs.get(cId);

						String proofFile = storeDataDir + "/cId_" + cId + ".proof";

						ByteArrayOutputStream bOut = new ByteArrayOutputStream(248);
						try {
							new ObjectOutputStream(bOut).writeObject(proofsToSave);
						} catch (IOException ex) {
							logger.error("Failed to serialize consensus message", ex);
						}
						byte[] epochProof = bOut.toByteArray();
						
						try {
							raf = new RandomAccessFile(proofFile, "rws");
							fd = raf.getFD();
							raf.write(epochProof);
							fd.sync();
							raf.close();
						} catch (Exception e) {
							e.printStackTrace();
						}
						
						/*try {
							fos = new FileOutputStream(proofFile, false);
							fd = fos.getFD();
							fos.write(epochProof);
							fos.flush();
							fd.sync();
							fos.close();
						} catch (Exception e) {
							e.printStackTrace();
						}*/
						

						if (logger.isTraceEnabled()) {
							// Informative code, read and show the saved proofs.
							logger.info("READING PROOF FROM DISK, cId:{}", cId);
							try {
								byte[] proofs = Files.readAllBytes(new File(proofFile).toPath());

								Set<ConsensusMessage> proofsRead = (Set<ConsensusMessage>) (new ObjectInputStream(
										new ByteArrayInputStream(proofs)).readObject());
								Iterator<ConsensusMessage> itProof = proofsRead.iterator();
								while (itProof.hasNext()) {
									ConsensusMessage cm = (ConsensusMessage) itProof.next();
									logger.info("From file, cId:{}, Sender:{}", cm.getNumber(), cm.getSender());
									
									/*if(cm.getProof() instanceof byte[]) {
										//Signature sig = (Signature) cm.getProof();
										logger.info("\tSignature Proof, Sender:{}, Signarue:{}", cm.getSender(),  cm.getProof());
									}else {
										logger.info("\tNot a signature, {} " , cm.getProof().getClass().getTypeName());
									}*/
								}
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (ClassNotFoundException e) {
								e.printStackTrace();
							}catch (Exception e) {
								e.printStackTrace();
							}
						}
					}

				} catch (InterruptedException  e) {
					e.printStackTrace();
				}
				
				
				
			}
		}
	}
}
