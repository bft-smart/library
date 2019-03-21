/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.core;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.core.messages.TOMMessage;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author joao
 */
public class ReplyManager {
        
    private LinkedList<ReplyThread> threads;
    private int iteration;
    
    public ReplyManager(int numThreads, ServerCommunicationSystem cs) {
        
        this.threads = new LinkedList();
        this.iteration = 0;
        
        for (int i = 0; i < numThreads; i++) {
            this.threads.add(new ReplyThread(cs));
        }
        
        for (ReplyThread t : threads)
            t.start();
    }
    
    public void send (TOMMessage msg) {
        
        iteration++;
        threads.get((iteration % threads.size())).send(msg);

    }
}
class ReplyThread extends Thread {
        
    private LinkedBlockingQueue<TOMMessage> replies;
    private ServerCommunicationSystem cs = null;
    
    private final Lock queueLock = new ReentrantLock();
    private final Condition notEmptyQueue = queueLock.newCondition();
    
    private Map<Integer, Channel> channels;
    
    ReplyThread(ServerCommunicationSystem cs) {
        this.cs = cs;
        this.replies = new LinkedBlockingQueue<TOMMessage>();
        this.channels = new HashMap<>();
    }
    
    void send(TOMMessage msg) {
        
        try {
            queueLock.lock();
            replies.put(msg);
            notEmptyQueue.signalAll();
            queueLock.unlock();
        } catch (InterruptedException ex) {
            Logger.getLogger(ReplyThread.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public void run() {


        while (true) {

            try {
                
                LinkedList<TOMMessage> list = new LinkedList<>();
                
                queueLock.lock();
                while (replies.isEmpty()) notEmptyQueue.await(10, TimeUnit.MILLISECONDS);
                replies.drainTo(list);
                queueLock.unlock();
                
                for (TOMMessage msg : list) {
                    
                    cs.getClientsConn().send(new int[] {msg.getSender()}, msg.reply, false);             
                }
            } catch (InterruptedException ex) {
                LoggerFactory.getLogger(this.getClass()).error("Could not retrieve reply from queue",ex);
            }

        }

    }
}