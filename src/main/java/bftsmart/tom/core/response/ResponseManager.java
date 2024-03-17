package bftsmart.tom.core.response;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.core.messages.TOMMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ResponseManager implements IMessageSentListener {
	private final Logger logger = LoggerFactory.getLogger("bftsmart");
	private final ReplyThread[] replyThreads;
	private int nextThreadIndex;
	private final Map<Integer, ReplyThread> messagesCurrentlyBeingSent;
	private final Lock messagesCurrentlyBeingSentLock;

	public ResponseManager(int nThreads, ServerCommunicationSystem cs) {
		this.replyThreads = new ReplyThread[nThreads];
		logger.info("Creating {} reply threads", nThreads);
		messagesCurrentlyBeingSent = new HashMap<>();
		this.messagesCurrentlyBeingSentLock = new ReentrantLock(true);
		for (int i = 0; i < nThreads; i++) {
			ReplyThread replyThread = new ReplyThread(cs, this);
			this.replyThreads[i] = replyThread;
			replyThread.start();
		}
	}

	public synchronized void send(TOMMessage response, int receiver, boolean isHashed) {
		try {
			messagesCurrentlyBeingSentLock.lock();
			ReplyThread replyThread = messagesCurrentlyBeingSent.get(response.getId());
			if (replyThread != null) {
				replyThread.send(response, receiver, isHashed);//Reply thread will deal with duplicated message
				return;
			}
			replyThread = replyThreads[nextThreadIndex];
			messagesCurrentlyBeingSent.put(response.getId(), replyThread);
			replyThread.send(response, receiver, isHashed);

			nextThreadIndex = (nextThreadIndex + 1) % replyThreads.length;
		} finally {
			messagesCurrentlyBeingSentLock.unlock();
		}
	}

	public void shutdown() throws InterruptedException {
		for (ReplyThread replyThread : replyThreads) {
			replyThread.interrupt();
		}
		for (ReplyThread replyThread : replyThreads) {
			replyThread.join();
		}
	}

	@Override
	public void messageSent(TOMMessage msg) {
		messagesCurrentlyBeingSentLock.lock();
		messagesCurrentlyBeingSent.remove(msg.getId());
		messagesCurrentlyBeingSentLock.unlock();
	}
}
