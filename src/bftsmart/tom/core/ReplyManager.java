/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.core;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.server.Replier;
import io.netty.channel.Channel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.LoggerFactory;


/**
 *
 * @author joao
 */
public class ReplyManager extends Thread {
        
    private int currentReplies = 0;
    private boolean doWork = true;
    private LinkedBlockingQueue<TOMMessage> replies = null;
    private ServerCommunicationSystem cs = null;
    private Replier replier = null;
    
    private final Lock queueLock = new ReentrantLock();
    private final Condition notEmptyQueue = queueLock.newCondition();
    
    private Map<Integer, Channel> channels;
    
    public ReplyManager (ServerCommunicationSystem cs, Replier replier) {
        this.cs = cs;
        this.replies = new LinkedBlockingQueue<TOMMessage>();
        this.channels = new HashMap<>();
        this.replier = replier;
    }
    
    public void send(TOMMessage msg) {
        
        try {
            queueLock.lock();
            replies.put(msg);
            notEmptyQueue.signalAll();
            queueLock.unlock();
        } catch (InterruptedException ex) {
            LoggerFactory.getLogger(this.getClass()).error("Interruption error", ex);
        }
    }
    
    public int getPendingReplies() {
       return replies.size() + currentReplies;
   }
    
    public void run() {


        while (doWork) {

            try {
                currentReplies = 0;
                
                LinkedList<TOMMessage> list = new LinkedList<>();
                
                queueLock.lock();
                
                while (replies.isEmpty()) {
                    notEmptyQueue.await(10, TimeUnit.MILLISECONDS);
                    if (!doWork) break;
                }
                
                if (!doWork && replies.isEmpty()) {
                    queueLock.unlock();
                    break;
                }
                
                replies.drainTo(list);
                queueLock.unlock();
                
                currentReplies = list.size();
                
                for (TOMMessage msg : list) {
                    
                    replier.manageReply(msg.reply, msg.msgCtx);
                }
            } catch (InterruptedException ex) {
                LoggerFactory.getLogger(this.getClass()).error("Could not retrieve reply from queue",ex);
            }

        }

    }
}