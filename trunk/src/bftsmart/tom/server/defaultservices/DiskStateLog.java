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
package bftsmart.tom.server.defaultservices;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class DiskStateLog extends StateLog {

	private int id;
	public final static String DEFAULT_DIR = "files".concat(System
			.getProperty("file.separator"));
	private static final int INT_BYTE_SIZE = 4;
	private static final int EOF = 0;

	private RandomAccessFile log;
	private boolean syncLog;
	private String logPath;
	private String lastCkpPath;
	private boolean syncCkp;
	private boolean isToLog;
	private ReentrantLock checkpointLock = new ReentrantLock();
	private Map<Integer, Long> logPointers;
	
	public DiskStateLog(int id, byte[] initialState, byte[] initialHash,
			boolean isToLog, boolean syncLog, boolean syncCkp) {
		super(initialState, initialHash);
		this.id = id;
		this.isToLog = isToLog;
		this.syncLog = syncLog;
		this.syncCkp = syncCkp;
		this.logPointers = new HashMap<Integer, Long>();
		if (isToLog)
			createLogFile();
	}

	private void createLogFile() {
		logPath = DEFAULT_DIR + String.valueOf(id) + "."
				+ System.currentTimeMillis() + ".log";
		try {
			log = new RandomAccessFile(logPath, (syncLog ? "rwd" : "rw"));
			// PreAllocation
			/*
			 * log.setLength(TEN_MB); log.seek(0);
			 */
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Adds a message batch to the log. This batches should be added to the log
	 * in the same order in which they are delivered to the application. Only
	 * the 'k' batches received after the last checkpoint are supposed to be
	 * kept
	 * 
	 * @param batch
	 *            The batch of messages to be kept.
	 */
	public void addMessageBatch(byte[][] commands, int round, int leader) {
			CommandsInfo command = new CommandsInfo(commands, round, leader);
			
			if (isToLog)
				writeCommandToDisk(command);
	}

	private void writeCommandToDisk(CommandsInfo commandsInfo) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oos = new ObjectOutputStream(bos);
			oos.writeObject(commandsInfo);
			oos.flush();

			byte[] batchBytes = bos.toByteArray();

			ByteBuffer bf = ByteBuffer.allocate(2 * INT_BYTE_SIZE
					+ batchBytes.length);
			bf.putInt(batchBytes.length);
			bf.put(batchBytes);
			bf.putInt(EOF);
			
			log.write(bf.array());
			log.seek(log.length() - INT_BYTE_SIZE);// Next write will overwrite
													// the EOF mark
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
	    }
	}

	public void newCheckpoint(byte[] state, byte[] stateHash) {
		String ckpPath = DEFAULT_DIR + String.valueOf(id) + "."
				+ System.currentTimeMillis() + ".tmp";
		try {
			checkpointLock.lock();
			RandomAccessFile ckp = new RandomAccessFile(ckpPath,
					(syncCkp ? "rwd" : "rw"));

			ByteBuffer bf = ByteBuffer.allocate(state.length + stateHash.length
					+ 3 * INT_BYTE_SIZE);
			bf.putInt(state.length);
			bf.put(state);
			bf.putInt(stateHash.length);
			bf.put(stateHash);
			bf.putInt(EOF);

			byte[] ckpState = bf.array();
			
			ckp.write(ckpState);

			if (isToLog)
				deleteLogFile();
			deleteLastCkp();
			renameCkp(ckpPath);
			if (isToLog)
				createLogFile();
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			checkpointLock.unlock();
		}
	}

	private void renameCkp(String ckpPath) {
		String finalCkpPath = ckpPath.replace(".tmp", ".ckp");
		new File(ckpPath).renameTo(new File(finalCkpPath));
		lastCkpPath = finalCkpPath;
	}

	private void deleteLastCkp() {
		if (lastCkpPath != null)
			new File(lastCkpPath).delete();
	}

	private void deleteLogFile() {
		try {
			log.close();
			new File(logPath).delete();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Constructs a TransferableState using this log information
	 * 
	 * @param eid
	 *            Execution ID correspondent to desired state
	 * @return TransferableState Object containing this log information
	 */
	public DefaultApplicationState getApplicationState(int eid, boolean sendState) {
//		readingState = true;
		CommandsInfo[] batches = null;

		int lastCheckpointEid = getLastCheckpointEid();
		int lastEid = getLastEid();
		System.out.println("LAST CKP EID = " + lastCheckpointEid);
		System.out.println("EID = " + eid);
		System.out.println("LAST EID = " + lastEid);
		if (eid >= lastCheckpointEid && eid <= lastEid) {

			int size = eid - lastCheckpointEid;

			FileRecoverer fr = new FileRecoverer(id, DEFAULT_DIR);

//			if (size > 0 && sendState) {
			if (size > 0) {
				CommandsInfo[] recoveredBatches = fr.getLogState(size);

				batches = new CommandsInfo[size];

				for (int i = 0; i < size; i++)
					batches[i] = recoveredBatches[i];
			}
			
			checkpointLock.lock();
			byte[] ckpState = fr.getCkpState();
			byte[] ckpStateHash = fr.getCkpStateHash();
			checkpointLock.unlock();

			System.out.println("--- FINISHED READING STATE");
//			readingState = false;

//			return new DefaultApplicationState((sendState ? batches : null), lastCheckpointEid,
			return new DefaultApplicationState(batches, lastCheckpointEid,
					getLastCheckpointRound(), getLastCheckpointLeader(), eid,
					(sendState ? ckpState : null), ckpStateHash);

		}
		return null;
	}
	
	public void transferApplicationState(SocketChannel sChannel, int eid) {
		FileRecoverer fr = new FileRecoverer(id, DEFAULT_DIR);
		fr.transferCkpState(sChannel);
		
//		int lastCheckpointEid = getLastCheckpointEid();
//		int lastEid = getLastEid();
//		if (eid >= lastCheckpointEid && eid <= lastEid) {
//			int size = eid - lastCheckpointEid;
//			fr.transferLog(sChannel, size);
//		}
	}

	public void setLastEid(int eid, int checkpointPeriod, int checkpointPortion) {
		super.setLastEid(eid);
		// save the file pointer to retrieve log information later
		if((eid % checkpointPeriod) % checkpointPortion == checkpointPortion -1) {
			int ckpReplicaIndex = (((eid % checkpointPeriod) + 1) / checkpointPortion) -1;
			try {
				System.out.println(" --- Replica " + ckpReplicaIndex + " took checkpoint. My current log pointer is " + log.getFilePointer());
				logPointers.put(ckpReplicaIndex, log.getFilePointer());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Updates this log, according to the information contained in the
	 * TransferableState object
	 * 
	 * @param transState
	 *            TransferableState object containing the information which is
	 *            used to updated this log
	 */
	public void update(DefaultApplicationState transState) {
		newCheckpoint(transState.getState(), transState.getStateHash());
		setLastCheckpointEid(transState.getLastCheckpointEid());
	}

}
