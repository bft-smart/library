/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.core.timer;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.reconfiguration.ServerViewManager;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.leaderchange.LCMessage;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;


/**
 * This thread serves as a manager for all timers of pending requests.
 *
 */
public class RequestsTimer {

    private Timer timer = new Timer("request timer");
    private RequestTimerTask rtTask = null;
    private TOMLayer tomLayer; // TOM layer
    private long timeout;
    private long shortTimeout;
    private TreeSet<TOMMessage> watched = new TreeSet<TOMMessage>();
    private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    private boolean enabled = true;
    
    private ServerCommunicationSystem communication; // Communication system between replicas
    private ServerViewManager reconfManager; // Reconfiguration manager
    
    //private Storage st1 = new Storage(100000);
    //private Storage st2 = new Storage(10000);
    /**
     * Creates a new instance of RequestsTimer
     * @param tomLayer TOM layer
     */
    public RequestsTimer(TOMLayer tomLayer, ServerCommunicationSystem communication, ServerViewManager reconfManager) {
        this.tomLayer = tomLayer;
        
        this.communication = communication;
        this.reconfManager = reconfManager;
        
        this.timeout = this.reconfManager.getStaticConf().getRequestTimeout();
        this.shortTimeout = -1;
    }

    public void setShortTimeout(long shortTimeout) {
        this.shortTimeout = shortTimeout;
    }
    
    public void startTimer() {
        if (rtTask == null) {
            long t = (shortTimeout > -1 ? shortTimeout : timeout);
            //shortTimeout = -1;
            rtTask = new RequestTimerTask();
            timer.schedule(rtTask, t);
        }
    }
    
    public void stopTimer() {
        if (rtTask != null) {
            rtTask.cancel();
            rtTask = null;
        }
    }
    
    public void Enabled(boolean phase) {
        
        enabled = phase;
    }
    /**
     * Creates a timer for the given request
     * @param request Request to which the timer is being createf for
     */
    public void watch(TOMMessage request) {
        //long startInstant = System.nanoTime();
        rwLock.writeLock().lock();
        watched.add(request);
        if (watched.size() >= 1 && enabled) startTimer();
        rwLock.writeLock().unlock();
        /*
        st1.store(System.nanoTime() - startInstant);
        if (st1.getCount()==100000){
            System.out.println("Tamanho da lista watched: "+ watched.size());
            System.out.println("Media do RequestsTimer.watch(): "+st1.getAverage(false)/1000 + " us");
            st1.reset();
        }
         * */
    }

    /**
     * Cancels a timer for a given request
     * @param request Request whose timer is to be canceled
     */
    public void unwatch(TOMMessage request) {
        //long startInstant = System.nanoTime();
        rwLock.writeLock().lock();
        if (watched.remove(request) && watched.isEmpty()) stopTimer();
        rwLock.writeLock().unlock();
        /*
        st2.store(System.nanoTime() - startInstant);
        if (st2.getCount()==10000){
            System.out.println("Average of RequestsTimer.unwatch(): "+st2.getAverage(false)/1000 + " us");
            st2.reset();
        }
        */
    }

    /**
     * Cancels all timers for all messages
     */
    public void clearAll() {
        TOMMessage[] requests = new TOMMessage[watched.size()];
        rwLock.writeLock().lock();
        
        watched.toArray(requests);

        for (TOMMessage request : requests) {
            if (request != null && watched.remove(request) && watched.isEmpty() && rtTask != null) {
                rtTask.cancel();
                rtTask = null;
            }
        }
        rwLock.writeLock().unlock();
    }
    
    public void run_lc_protocol() {
     
        long t = (shortTimeout > -1 ? shortTimeout : timeout);
        
        //System.out.println("(RequestTimerTask.run) I SOULD NEVER RUN WHEN THERE IS NO TIMEOUT");
        rwLock.readLock().lock();

        LinkedList<TOMMessage> pendingRequests = new LinkedList<TOMMessage>();

        for (Iterator<TOMMessage> i = watched.iterator(); i.hasNext();) {
            TOMMessage request = i.next();
            if ((request.receptionTime + System.currentTimeMillis()) > t) {
                pendingRequests.add(request);
            } else {
                break;
            }
        }

        if (!pendingRequests.isEmpty()) {
            for (ListIterator<TOMMessage> li = pendingRequests.listIterator(); li.hasNext(); ) {
                TOMMessage request = li.next();
                if (!request.timeout) {

                    request.signed = request.serializedMessageSignature != null;
                    tomLayer.forwardRequestToLeader(request);
                    request.timeout = true;
                    li.remove();
                }
            }

            if (!pendingRequests.isEmpty()) {
                System.out.println("Timeout for messages: " + pendingRequests);
                //Logger.debug = true;
                //tomLayer.requestTimeout(pendingRequests);
                //if (reconfManager.getStaticConf().getProcessId() == 4) Logger.debug = true;
                tomLayer.triggerTimeout(pendingRequests);
            }
            else {
                rtTask = new RequestTimerTask();
                timer.schedule(rtTask, t);
            }
        } else {
            rtTask = null;
            timer.purge();
        }

        rwLock.readLock().unlock();

    }
    
    class RequestTimerTask extends TimerTask {

        @Override
        /**
         * This is the code for the TimerTask. It executes the timeout for the first
         * message on the watched list.
         */
        public void run() {

            int[] myself = new int[1];
            myself[0] = reconfManager.getStaticConf().getProcessId();

            communication.send(myself, new LCMessage(-1, TOMUtil.TRIGGER_LC_LOCALLY, -1, null));

        }
    }
}
