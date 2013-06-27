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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;

public class FileRecoverer {

	private static final int EOF = 0;
	private int processId;
	private String defaultDirectory;
	private byte[] ckpHash;

	public FileRecoverer(int id, String filesDir) {
		this.processId = id;
		this.defaultDirectory = filesDir;
	}

	public CommandsInfo[] getLogState(int index) {
		String file = getTSLogsPathes(".log");
		RandomAccessFile log = null;

		if ((log = openLogFile(file)) != null) {
			System.out.println("GETTING STATE FROM " + file);

			CommandsInfo[] logState = recoverLogState(log, index);

			try {
				log.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return logState;
		}

		return null;
	}

	/**
	 * Recover portions of the log for collaborative state transfer.
	 * @param start the index for which the commands start to be collected
	 * @param number the number of commands retrieved
	 * @return The commands for the period selected
	 */
	public CommandsInfo[] getLogState(long pointer, int startOffset,  int number) {
		String file = getTSLogsPathes(".log");
		RandomAccessFile log = null;

		if ((log = openLogFile(file)) != null) {
			System.out.println("GETTING STATE FROM " + file);

			CommandsInfo[] logState = recoverLogState(log, pointer, startOffset, number);

			try {
				log.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return logState;
		}

		return null;
	}

	public byte[] getCkpState() {
		String file = getTSLogsPathes(".ckp");
		RandomAccessFile ckp = null;

		if ((ckp = openLogFile(file)) != null) {
			System.out.println("GETTING STATE FROM " + file);

			byte[] ckpState = recoverCkpState(ckp);

			try {
				ckp.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

			return ckpState;
		}

		return null;
	}

	public void recoverCkpHash() {
		String file = getTSLogsPathes(".ckp");
		RandomAccessFile ckp = null;

		if ((ckp = openLogFile(file)) != null) {
			System.out.println("GETTING HASH FROM " + file);
			byte[] ckpHash = null;
			try {
				int ckpSize = ckp.readInt();
				ckp.skipBytes(ckpSize);
				int hashLength = ckp.readInt();
				ckpHash = new byte[hashLength];
				ckp.read(ckpHash);
				System.out.println("--- Last ckp size: " + ckpSize + " Last ckp hash: " + Arrays.toString(ckpHash));
			} catch (Exception e) {
				e.printStackTrace();
				System.err
				.println("State recover was aborted due to an unexpected exception");
			}
			this.ckpHash = ckpHash;
		}
	}

	private byte[] recoverCkpState(RandomAccessFile ckp) {
		byte[] ckpState = null;
		try {
			long logLength = ckp.length();
			boolean mayRead = true;
			while (mayRead) {

				try {
					if (ckp.getFilePointer() < logLength) {
						int size = ckp.readInt();

						if (size > 0) {
							ckpState = new byte[size];//ckp state
							int read = ckp.read(ckpState);
							if (read == size) {
								int hashSize = ckp.readInt();
								if (size > 0) {
									ckpHash = new byte[hashSize];//ckp hash
									read = ckp.read(ckpHash);
									if (read == hashSize) {
										mayRead = false;
									}else{
										ckpHash = null;
										ckpState = null;
									}
								}
							} else {
								mayRead = false;
								ckp = null;
							}
						} else
							mayRead = false;
					} else {
						mayRead = false;
					}
				} catch (Exception e) {
					e.printStackTrace();
					ckp = null;
					mayRead = false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err
			.println("State recover was aborted due to an unexpected exception");
		}

		return ckpState;
	}

	public void transferLog(SocketChannel sChannel, int index) {
		String file = getTSLogsPathes(".log");
		RandomAccessFile log = null;

		if ((log = openLogFile(file)) != null) {
			System.out.println("GETTING STATE FROM LOG " + file);
			transferLog(log, sChannel, index);
		}
	}

	private void transferLog(RandomAccessFile logFile, SocketChannel sChannel, int index) {
		try {
			long totalBytes = logFile.length();
			System.out.println("---Called transferLog." + totalBytes + " " + (sChannel == null));
			FileChannel fileChannel = logFile.getChannel();
			long bytesTransfered = 0;
			while(bytesTransfered < totalBytes) {
				long bufferSize = 65536;
				if(totalBytes  - bytesTransfered < bufferSize) {
					bufferSize = (int)(totalBytes - bytesTransfered);
					if(bufferSize <= 0)
						bufferSize = (int)totalBytes;
				}
				long bytesSent = fileChannel.transferTo(bytesTransfered, bufferSize, sChannel);
				if(bytesSent > 0) {
					bytesTransfered += bytesSent;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err
			.println("State recover was aborted due to an unexpected exception");
		}
	}

	public void transferCkpState(SocketChannel sChannel) {
		String file = getTSLogsPathes(".ckp");
		RandomAccessFile ckp = null;

		if ((ckp = openLogFile(file)) != null) {
			System.out.println("GETTING STATE FROM " + file);

			transferCkpState(ckp, sChannel);

			try {
				ckp.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void transferCkpState(RandomAccessFile ckp, SocketChannel sChannel) {
		try {
			long milliInit = System.currentTimeMillis();
			System.out.println("--- Sending checkpoint." + ckp.length() + " " + (sChannel == null));
			FileChannel fileChannel = ckp.getChannel();
			long totalBytes = ckp.length();
			long bytesTransfered = 0;
			while(bytesTransfered < totalBytes) {
				long bufferSize = 65536;
				if(totalBytes  - bytesTransfered < bufferSize) {
					bufferSize = (int)(totalBytes - bytesTransfered);
					if(bufferSize <= 0)
						bufferSize = (int)totalBytes;
				}
				long bytesRead = fileChannel.transferTo(bytesTransfered, bufferSize, sChannel);
				if(bytesRead > 0) {
					bytesTransfered += bytesRead;
				}
			}
			System.out.println("---Took " + (System.currentTimeMillis() - milliInit) + " milliseconds to transfer the checkpoint");
			fileChannel.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err
			.println("State recover was aborted due to an unexpected exception");
		}
	}

	public byte[] getCkpStateHash() {
		return ckpHash;
	}

	private String getTSLogsPathes(String extention) {

		File directory = new File(defaultDirectory);

		ArrayList<String> files = new ArrayList<String>();

		if (directory.isDirectory()) {
			File[] serverLogs = directory.listFiles(new FileListFilter(
					processId, extention));

			for (File f : serverLogs) {
				files.add(f.getAbsolutePath());
			}
		}

		return files.get(0);// Only one log file
	}

	private RandomAccessFile openLogFile(String file) {

		try {
			return new RandomAccessFile(file, "r");

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;
	}

	private class FileListFilter implements FilenameFilter {

		private int id;
		private String extention;

		public FileListFilter(int id, String extention) {
			this.id = id;
			this.extention = extention;
		}

		public boolean accept(File directory, String filename) {
			boolean fileOK = false;

			if (id >= 0) {
				if (filename.startsWith(id + ".")
						&& filename.endsWith(extention)) {
					fileOK = true;
				}
			}

			return fileOK;
		}
	}

	private CommandsInfo[] recoverLogState(RandomAccessFile log, int endOffset) {
		try {
			long logLength = log.length();
			ArrayList<CommandsInfo> state = new ArrayList<CommandsInfo>();
			int recoveredBatches = 0;
			boolean mayRead = true;
			System.out.println("filepointer: " + log.getFilePointer() + " loglength " + logLength + " endoffset " + endOffset);
			while (mayRead) {
				try {
					if (log.getFilePointer() < logLength) {
						int size = log.readInt();
						if (size > 0) {
							byte[] bytes = new byte[size];
							int read = log.read(bytes);
							if (read == size) {
								ByteArrayInputStream bis = new ByteArrayInputStream(
										bytes);
								ObjectInputStream ois = new ObjectInputStream(
										bis);
								state.add((CommandsInfo) ois.readObject());
								if (++recoveredBatches == endOffset) {
									System.out.println("read all " + endOffset + " log messages");
									return state.toArray(new CommandsInfo[state.size()]);
								}
							} else {
								mayRead = false;
								System.out.println("STATE CLEAR");
								state.clear();
							}
						} else {
							System.out.println("ELSE 1");
							mayRead = false;
						}
					} else {
						System.out.println("ELSE 2 " + recoveredBatches);
						mayRead = false;
					}
				} catch (Exception e) {
					e.printStackTrace();
					state.clear();
					mayRead = false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err
			.println("State recover was aborted due to an unexpected exception");
		}

		return null;
	}

	/**
	 * Searches the log file and retrieves the portion selected.
	 * @param log The log file
	 * @param start The offset to start retrieving commands
	 * @param number The number of commands retrieved
	 * @return The commands for the period selected
	 */
	private CommandsInfo[] recoverLogState(RandomAccessFile log, long pointer, int startOffset, int number) {
		try {
			long logLength = log.length();
			ArrayList<CommandsInfo> state = new ArrayList<CommandsInfo>();
			int recoveredBatches = 0;
			boolean mayRead = true;

			log.seek(pointer);

			int index = 0;
			while(index < startOffset) {
				int size = log.readInt();
				byte[] bytes = new byte[size];
				log.read(bytes);
				index++;
			}

			while (mayRead) {

				try {
					if (log.getFilePointer() < logLength) {
						int size = log.readInt();

						if (size > 0) {
							byte[] bytes = new byte[size];
							int read = log.read(bytes);
							if (read == size) {
								ByteArrayInputStream bis = new ByteArrayInputStream(
										bytes);
								ObjectInputStream ois = new ObjectInputStream(
										bis);

								state.add((CommandsInfo) ois.readObject());

								if (++recoveredBatches == number) {
									return state.toArray(new CommandsInfo[state.size()]);
								}
							} else {
								System.out.println("recoverLogState (pointer,offset,number) STATE CLEAR");
								mayRead = false;
								state.clear();
							}
						} else {
							System.out.println("recoverLogState (pointer,offset,number) ELSE 1");
							mayRead = false;
						}
					} else {
						System.out.println("recoverLogState (pointer,offset,number) ELSE 2 " + recoveredBatches);
						mayRead = false;
					}
				} catch (Exception e) {
					e.printStackTrace();
					state.clear();
					mayRead = false;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.err
			.println("State recover was aborted due to an unexpected exception");
		}

		return null;
	}

}
