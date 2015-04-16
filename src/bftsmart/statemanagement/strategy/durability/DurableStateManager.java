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
package bftsmart.statemanagement.strategy.durability;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.locks.ReentrantLock;

import bftsmart.consensus.executionmanager.ExecutionManager;
import bftsmart.consensus.messages.PaxosMessage;
import bftsmart.reconfiguration.views.View;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.SMMessage;
import bftsmart.statemanagement.strategy.BaseStateManager;
import bftsmart.statemanagement.strategy.durability.CSTState;
import bftsmart.tom.core.DeliveryThread;
import bftsmart.tom.core.TOMLayer;
import bftsmart.tom.leaderchange.LCManager;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.durability.DurabilityCoordinator;
import bftsmart.tom.util.Logger;
import bftsmart.tom.util.TOMUtil;

public class DurableStateManager extends BaseStateManager {

	private LCManager lcManager;
	private ExecutionManager execManager;

	private ReentrantLock lockTimer = new ReentrantLock();
	private Timer stateTimer = null;
	private final static long INIT_TIMEOUT = 40000;
	private long timeout = INIT_TIMEOUT;

	private CSTRequestF1 cstRequest;

	private CSTState stateCkp;
	private CSTState stateLower;
	private CSTState stateUpper;

	@Override
	public void init(TOMLayer tomLayer, DeliveryThread dt) {
		SVController = tomLayer.controller;

		this.tomLayer = tomLayer;
		this.dt = dt;
		this.lcManager = tomLayer.getLCManager();
		this.execManager = tomLayer.execManager;

		state = null;
		lastEid = 1;
		waitingEid = -1;
		
		appStateOnly = false;
	}

	@Override
	protected void requestState() {
		if (tomLayer.requestsTimer != null)
			tomLayer.requestsTimer.clearAll();

		int myProcessId = SVController.getStaticConf().getProcessId();
		int[] otherProcesses = SVController.getCurrentViewOtherAcceptors();
		int globalCkpPeriod = SVController.getStaticConf()
				.getGlobalCheckpointPeriod();

		CSTRequestF1 cst = new CSTRequestF1(waitingEid);
		cst.defineReplicas(otherProcesses, globalCkpPeriod, myProcessId);
		this.cstRequest = cst;
		CSTSMMessage cstMsg = new CSTSMMessage(myProcessId, waitingEid,
				TOMUtil.SM_REQUEST, cst, null, null, -1, -1);
		tomLayer.getCommunication().send(
				SVController.getCurrentViewOtherAcceptors(), cstMsg);

		System.out
				.println("(TOMLayer.requestState) I just sent a request to the other replicas for the state up to EID "
						+ waitingEid);

		TimerTask stateTask = new TimerTask() {
			public void run() {
				int[] myself = new int[1];
				myself[0] = SVController.getStaticConf().getProcessId();
				tomLayer.getCommunication().send(
						myself,
						new CSTSMMessage(-1, waitingEid,
								TOMUtil.TRIGGER_SM_LOCALLY, null, null, null,
								-1, -1));
			}
		};

		stateTimer = new Timer("state timer");
		timeout = timeout * 2;
		stateTimer.schedule(stateTask, timeout);
	}

	@Override
	public void stateTimeout() {
		lockTimer.lock();
		Logger.println("(StateManager.stateTimeout) Timeout for the replica that was supposed to send the complete state. Changing desired replica.");
		System.out.println("Timeout no timer do estado!");
		if (stateTimer != null)
			stateTimer.cancel();
		reset();
		requestState();
		lockTimer.unlock();
	}

	@Override
	public void SMRequestDeliver(SMMessage msg, boolean isBFT) {
		System.out.println("(TOMLayer.SMRequestDeliver) invoked method");
		Logger.println("(TOMLayer.SMRequestDeliver) invoked method");
		if (SVController.getStaticConf().isStateTransferEnabled()
				&& dt.getRecoverer() != null) {
			Logger.println("(TOMLayer.SMRequestDeliver) The state transfer protocol is enabled");
			Logger.println("(TOMLayer.SMRequestDeliver) I received a state request for EID "
					+ msg.getEid() + " from replica " + msg.getSender());
			CSTSMMessage cstMsg = (CSTSMMessage) msg;
			CSTRequestF1 cstConfig = cstMsg.getCstConfig();
			boolean sendState = cstConfig.getCheckpointReplica() == SVController
					.getStaticConf().getProcessId();
			if (sendState)
				Logger.println("(TOMLayer.SMRequestDeliver) I should be the one sending the state");

			System.out.println("--- state asked");

			int[] targets = { msg.getSender() };
			InetSocketAddress address = SVController.getCurrentView().getAddress(
					SVController.getStaticConf().getProcessId());
			String myIp = address.getHostName();
			int myId = SVController.getStaticConf().getProcessId();
			int port = 4444 + myId;
			address = new InetSocketAddress(myIp, port);
			cstConfig.setAddress(address);
			CSTSMMessage reply = new CSTSMMessage(myId, msg.getEid(),
					TOMUtil.SM_REPLY, cstConfig, null,
					SVController.getCurrentView(), lcManager.getLastReg(),
					tomLayer.lm.getCurrentLeader());

			StateSenderServer stateServer = new StateSenderServer(port);
			stateServer.setRecoverable(dt.getRecoverer());
			stateServer.setRequest(cstConfig);
			new Thread(stateServer).start();

			tomLayer.getCommunication().send(targets, reply);

		}
	}

	@Override
	public void SMReplyDeliver(SMMessage msg, boolean isBFT) {
		lockTimer.lock();
		CSTSMMessage reply = (CSTSMMessage) msg;
		if (SVController.getStaticConf().isStateTransferEnabled()) {
			Logger.println("(TOMLayer.SMReplyDeliver) The state transfer protocol is enabled");
			System.out
					.println("(TOMLayer.SMReplyDeliver) I received a state reply for EID "
							+ reply.getEid()
							+ " from replica "
							+ reply.getSender());

			System.out.println("--- Received eid: " + reply.getEid()
					+ ". Waiting " + waitingEid);
			if (waitingEid != -1 && reply.getEid() == waitingEid) {

				int currentRegency = -1;
				int currentLeader = -1;
				View currentView = null;

				if (!appStateOnly) {
					senderRegencies.put(reply.getSender(), reply.getRegency());
					senderLeaders.put(reply.getSender(), reply.getLeader());
					senderViews.put(reply.getSender(), reply.getView());
					if (moreThan2F_Regencies(reply.getRegency()))
						currentRegency = reply.getRegency();
					if (moreThan2F_Leaders(reply.getLeader()))
						currentLeader = reply.getLeader();
					if (moreThan2F_Views(reply.getView())) {
						currentView = reply.getView();
						if (!currentView.isMember(SVController.getStaticConf()
								.getProcessId())) {
							System.out.println("Not a member!");
						}
					}
				} else {
					currentLeader = tomLayer.lm.getCurrentLeader();
					currentRegency = lcManager.getLastReg();
					currentView = SVController.getCurrentView();
				}

				Logger.println("(TOMLayer.SMReplyDeliver) The reply is for the EID that I want!");

				InetSocketAddress address = reply.getCstConfig().getAddress();
				Socket clientSocket;
				ApplicationState stateReceived = null;
				try {
					clientSocket = new Socket(address.getHostName(),
							address.getPort());
					ObjectInputStream in = new ObjectInputStream(
							clientSocket.getInputStream());
					stateReceived = (ApplicationState) in.readObject();
				} catch (UnknownHostException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ClassNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				if (stateReceived instanceof CSTState) {
					senderStates.put(reply.getSender(), stateReceived);
					if (reply.getSender() == cstRequest.getCheckpointReplica())
						this.stateCkp = (CSTState) stateReceived;
					if (reply.getSender() == cstRequest.getLogLower())
						this.stateLower = (CSTState) stateReceived;
					if (reply.getSender() == cstRequest.getLogUpper())
						this.stateUpper = (CSTState) stateReceived;
				}

				if (senderStates.size() == 3) {

					CommandsInfo[] lowerLog = stateLower.getLogLower();
					CommandsInfo[] upperLog = stateUpper.getLogUpper();
					System.out.print("lowerLog ");
					if (lowerLog != null)
						System.out.println(lowerLog.length);
					System.out.print("upperLog ");
					if (upperLog != null)
						System.out.println(upperLog.length);

					boolean haveState = false;
					byte[] lowerbytes = TOMUtil.getBytes(lowerLog);
					System.out.println("Log lower bytes size: "
							+ lowerbytes.length);
					byte[] upperbytes = TOMUtil.getBytes(upperLog);
					System.out.println("Log upper bytes size: "
							+ upperbytes.length);

					byte[] lowerLogHash = TOMUtil.computeHash(lowerbytes);
					byte[] upperLogHash = TOMUtil.computeHash(upperbytes);

					// validate lower log
					if (Arrays.equals(stateCkp.getHashLogLower(), lowerLogHash))
						haveState = true;
					else
						System.out.println("Lower log don't match");
					// validate upper log
					if (!haveState || !Arrays.equals(stateCkp.getHashLogUpper(), upperLogHash)) {
						haveState = false;
						System.out.println("Upper log don't match");
					}

					CSTState statePlusLower = new CSTState(stateCkp.getSerializedState(),
							TOMUtil.getBytes(stateCkp.getSerializedState()),
							stateLower.getLogLower(), stateCkp.getHashLogLower(), null, null,
							stateCkp.getCheckpointEid(), stateUpper.getCheckpointEid());

					if (haveState) { // validate checkpoint
						System.out.println("validating checkpoint!!!");
						dt.getRecoverer().setState(statePlusLower);
						byte[] currentStateHash = ((DurabilityCoordinator) dt.getRecoverer()).getCurrentStateHash();
						if (!Arrays.equals(currentStateHash, stateUpper.getHashCheckpoint())) {
							System.out.println("ckp hash don't match");
							haveState = false;
						}
					}

					System.out.println("-- current regency: " + currentRegency);
					System.out.println("-- current leader: " + currentLeader);
					System.out.println("-- current view: " + currentView);
					if (currentRegency > -1 && currentLeader > -1
							&& currentView != null && haveState) {
						System.out.println("---- RECEIVED VALID STATE ----");

						Logger.println("(TOMLayer.SMReplyDeliver) The state of those replies is good!");
						Logger.println("(TOMLayer.SMReplyDeliver) EID State requested: " + reply.getEid());
						Logger.println("(TOMLayer.SMReplyDeliver) EID State received: "	+ stateUpper.getLastEid());

						lcManager.setLastReg(currentRegency);
						lcManager.setNextReg(currentRegency);
						lcManager.setNewLeader(currentLeader);

						tomLayer.lm.setNewLeader(currentLeader);
						
						System.out.print("trying to acquire deliverlock");
						dt.deliverLock();
						System.out.println("acquired");

						// this makes the isRetrievingState() evaluates to false
						waitingEid = -1;
						dt.update(stateUpper);

						// Deal with stopped messages that may come from
						// synchronization phase
						if (!appStateOnly && execManager.stopped()) {
							Queue<PaxosMessage> stoppedMsgs = execManager.getStoppedMsgs();
							for (PaxosMessage stopped : stoppedMsgs) {
								if (stopped.getNumber() > state.getLastEid())
									execManager.addOutOfContextMessage(stopped);
							}
							execManager.clearStopped();
							execManager.restart();
						}

						System.out.println("Processing out of context messages");
						tomLayer.processOutOfContext();

						if (SVController.getCurrentViewId() != currentView.getId()) {
							System.out.println("Installing current view!");
							SVController.reconfigureTo(currentView);
						}
						
						isInitializing = false;

						dt.canDeliver();
						dt.deliverUnlock();

						reset();

						System.out.println("I updated the state!");

						tomLayer.requestsTimer.Enabled(true);
						tomLayer.requestsTimer.startTimer();
						if (stateTimer != null)
							stateTimer.cancel();

						if (appStateOnly) {
							appStateOnly = false;
							tomLayer.resumeLC();
						}
					} else if (state == null
							&& (SVController.getCurrentViewN() / 2) < getReplies()) {
						System.out.println("---- DIDNT RECEIVE STATE ----");

						Logger.println("(TOMLayer.SMReplyDeliver) I have more than "
								+ (SVController.getCurrentViewN() / 2)
								+ " messages that are no good!");

						waitingEid = -1;
						reset();

						if (stateTimer != null)
							stateTimer.cancel();

						if (appStateOnly) {
							requestState();
						}
					} else if (!haveState) {
						System.out.println("---- RECEIVED INVALID STATE  ----");

						Logger.println("(TOMLayer.SMReplyDeliver) The replica from which I expected the state, sent one which doesn't match the hash of the others, or it never sent it at all");

						reset();
						requestState();

						if (stateTimer != null)
							stateTimer.cancel();
					}
				}
			}
		}
		lockTimer.unlock();
	}
	
}
