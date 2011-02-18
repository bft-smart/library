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

package navigators.smart.paxosatwar.requesthandler.timer;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Logger;


/**
 * This thread serves as a manager for all timers of pending requests.
 *
 */
public class RequestsTimer {

    private Timer timer = new Timer("request timer");
    private RequestTimerTask rtTask = null;
    private RequestHandler reqhandler; // TOM layer
    private long timeout;
    private TreeSet<TOMMessage> watched = new TreeSet<TOMMessage>();
    private ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    //private Storage st1 = new Storage(100000);
    //private Storage st2 = new Storage(10000);
    /**
     * Creates a new instance of RequestsTimer
     * @param reqhandler The requesthandler that handles incoming requests
     * @param timeout The timeout for requests to be delivered
     */
    public RequestsTimer(RequestHandler reqhandler, long timeout) {
        this.reqhandler = reqhandler;
        this.timeout = timeout;
    }

    /**
     * Creates a timer for the given request
     * @param request Request to which the timer is being createf for
     */
    public void watch(TOMMessage request) {
        //long startInstant = System.nanoTime();
        rwLock.writeLock().lock();
        watched.add(request);
        if (watched.size() == 1 && rtTask == null) {
            rtTask = new RequestTimerTask();
            timer.schedule(rtTask, timeout);
        }
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
        if (watched.remove(request) && watched.isEmpty() && rtTask != null) {
            rtTask.cancel();
            rtTask = null;
        }
        rwLock.writeLock().unlock();
        /*
        st2.store(System.nanoTime() - startInstant);
        if (st2.getCount()==10000){
            System.out.println("Media do RequestsTimer.unwatch(): "+st2.getAverage(false)/1000 + " us");
            st2.reset();
        }
        */
    }

    class RequestTimerTask extends TimerTask {

        @Override
        /**
         * This is the code for the TimerTask. It executes the timeout for the first
         * message on the watched list.
         */
        public void run() {
            if(Logger.debug)
                Logger.println("(RequestTimerTask.run) EU NUNCA DEVIA CORRER QUANDO NAO HA TIMEOUTS");
            rwLock.readLock().lock();

            LinkedList<TOMMessage> pendingRequests = new LinkedList<TOMMessage>();

            for (Iterator<TOMMessage> i = watched.iterator(); i.hasNext();) {
                TOMMessage request = i.next();
                if ((request.receptionTime + System.currentTimeMillis()) > timeout) {
                    pendingRequests.add(request);
                } else {
                    break;
                }
            }

            if (!pendingRequests.isEmpty()) {
                //Try to send the request to the leader in case the client did not send
                // the message to the leader
                for (ListIterator<TOMMessage> li = pendingRequests.listIterator(); li.hasNext(); ) {
                    TOMMessage request = li.next();
                    if (!request.timeout) {
                        reqhandler.forwardRequestToLeader(request);
                        request.timeout = true;
                        li.remove();
                    }
                }
                // the leader failed to propose this request. Elect a new leader
                if (!pendingRequests.isEmpty()) {
                    if(Logger.debug)
                        Logger.println("Timeout for messages: " + pendingRequests);
                    reqhandler.requestTimeout(pendingRequests);
                }

                rtTask = new RequestTimerTask();
                timer.schedule(rtTask, timeout);
            } else {
                rtTask = null;
                timer.purge();
            }

            rwLock.readLock().unlock();
        }
    }
}
