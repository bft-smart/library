package bftsmart.tom.core;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.slf4j.LoggerFactory;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.core.messages.TOMMessage;

/**
 * @author Joao; Tulio Ribeiro
 */
public class ReplyManager {

	ReplyThread rt;
	private LinkedList<ReplyThread> threads;
	private int iteration;
	
	public ReplyManager(int numThreads, ServerCommunicationSystem cs) {
		rt = new ReplyThread(cs);
		this.threads = new LinkedList<>();

		for (int i = 0; i < numThreads; i++) {
            this.threads.add(new ReplyThread(cs));
        }
        
        for (ReplyThread t : threads)
            new Thread(t).start();
	}

	public void send(TOMMessage msg) {
		iteration++;
        threads.get((iteration % threads.size())).send(msg);
	}
}

class ReplyThread implements Runnable {
	private LinkedBlockingQueue<TOMMessage> replies;
	private ServerCommunicationSystem cs = null;

	ReplyThread(ServerCommunicationSystem cs) {
		this.cs = cs;
		this.replies = new LinkedBlockingQueue<TOMMessage>();
	}

	void send(TOMMessage msg) {
		try {
			replies.put(msg);
		} catch (InterruptedException ex) {
			Logger.getLogger(ReplyThread.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	@Override
	public void run() {
		while (true) {
			try {
				LinkedList<TOMMessage> list = new LinkedList<>();
				list.add(replies.take());
				replies.drainTo(list);
				for (TOMMessage msg : list) {
					cs.getClientsConn().send(new int[] { msg.getSender() }, msg.reply, false);
				}
			} catch (InterruptedException ex) {
				LoggerFactory.getLogger(this.getClass()).error("Could not retrieve reply from queue", ex);
			}
		}
	}
}
