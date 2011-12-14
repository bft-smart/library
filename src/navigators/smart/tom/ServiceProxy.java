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
package navigators.smart.tom;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.reconfiguration.ReconfigureReply;
import navigators.smart.reconfiguration.views.View;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.core.messages.TOMMessageType;
import navigators.smart.tom.util.Extractor;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;

/**
 * This class implements a TOMSender and represents a proxy to be used on the
 * client side of the replicated system.
 * It sends a request to the replicas, receives the reply, and delivers it to
 * the application.
 */
public class ServiceProxy extends TOMSender {

    // Locks for send requests and receive replies
    private ReentrantLock canReceiveLock = new ReentrantLock();
    private ReentrantLock canSendLock = new ReentrantLock();
    private Semaphore sm = new Semaphore(0);
    private int reqId = -1; // request id
    private int replyQuorum = 0; // size of the reply quorum
    private TOMMessage replies[] = null; // Replies from replicas are stored here
    private int receivedReplies = 0; // Number of received replies
    private TOMMessage response = null; // Reply delivered to the application
    private int invokeTimeout = 60;
    private Comparator<byte[]> comparator;
    private Extractor extractor;

    /**
     * Constructor
     *
     * @see bellow
     */
    public ServiceProxy(int processId) {
        this(processId, null, null, null);
    }

    /**
     * Constructor
     *
     * @see bellow
     */
    public ServiceProxy(int processId, String configHome) {
        this(processId, configHome, null, null);
    }

    /**
     * Constructor
     *
     * @param processId Process id for this client (should be different from replicas)
     * @param configHome Configuration directory for BFT-SMART
     * @param replyComparator used for comparing replies from different servers
     *                        to extract one returned by f+1
     * @param replyExtractor used for extracting the response from the matching
     *                       quorum of replies
     */
    public ServiceProxy(int processId, String configHome,
            Comparator<byte[]> replyComparator, Extractor replyExtractor) {
        if (configHome == null) {
            init(processId);
        } else {
            init(processId, configHome);
        }

        replyQuorum = (int) Math.ceil((getViewManager().getCurrentViewN()
                + getViewManager().getCurrentViewF()) / 2) + 1;
//        replyQuorum = getViewManager().getCurrentViewF() + 1;

        replies = new TOMMessage[getViewManager().getCurrentViewN()];

        comparator = (replyComparator != null) ? replyComparator : new Comparator<byte[]>() {
            @Override
            public int compare(byte[] o1, byte[] o2) {
                return Arrays.equals(o1, o2) ? 0 : -1;
            }
        };

        extractor = (replyExtractor != null) ? replyExtractor : new Extractor() {

            @Override
            public TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived) {
                return replies[lastReceived];
            }
        };
    }

    /**
     * Get the amount of time (in seconds) that this proxy will wait for
     * servers replies before returning null.
     *
     * @return the invokeTimeout
     */
    public int getInvokeTimeout() {
        return invokeTimeout;
    }

    /**
     * Set the amount of time (in seconds) that this proxy will wait for
     * servers replies before returning null.
     *
     * @param invokeTimeout the invokeTimeout to set
     */
    public void setInvokeTimeout(int invokeTimeout) {
        this.invokeTimeout = invokeTimeout;
    }

    /**
     * This method sends a request to the replicas, and returns the related reply. This method is
     * thread-safe.
     *
     * @param request Request to be sent
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request) {
        return invoke(request, TOMMessageType.REQUEST);
    }

    public byte[] invoke(byte[] request, boolean readOnly) {
        TOMMessageType type = (readOnly) ? TOMMessageType.READONLY_REQUEST
                : TOMMessageType.REQUEST;
        return invoke(request, type);
    }

    /**
     * This method sends a request to the replicas, and returns the related reply.
     * If the servers take more than invokeTimeout seconds the method returns null.
     * This method is thread-safe.
     *
     * @param request Request to be sent
     * @param reqType TOM_NORMAL_REQUESTS for service requests, and other for
     *        reconfig requests.
     * @return The reply from the replicas related to request
     */
    public byte[] invoke(byte[] request, TOMMessageType reqType) {
        canSendLock.lock();

        // Clean all statefull data to prepare for receiving next replies
        Arrays.fill(replies, null);
        receivedReplies = 0;
        response = null;

        // Send the request to the replicas, and get its ID
        reqId = generateRequestId();
        TOMulticast(request, reqId, reqType);

        Logger.println("Sending request (" + reqType + ") with reqId=" + reqId);
        Logger.println("Expected number of matching replies: " + replyQuorum);

        // This instruction blocks the thread, until a response is obtained.
        // The thread will be unblocked when the method replyReceived is invoked
        // by the client side communication system
        try {
            if (!this.sm.tryAcquire(invokeTimeout, TimeUnit.SECONDS)) {
                Logger.println("###################TIMEOUT#######################");
                Logger.println("Reply timeout for reqId=" + reqId);
                canSendLock.unlock();
                return null;
            }
        } catch (InterruptedException ex) {
        }

        Logger.println("Response extracted = " + response);

        byte[] ret = null;

        if (response == null) {
            //the response can be null if n-f replies are received but there isn't
            //a replyQuorum of matching replies
            Logger.println("Received n-f replies and no response could be extracted.");

            canSendLock.unlock();
            if (reqType == TOMMessageType.READONLY_REQUEST) {
                //invoke the operation again, whitout the read-only flag
                Logger.println("###################RETRY#######################");
                return invoke(request);
            } else {
                throw new RuntimeException("Received n-f replies without f+1 of them matching.");
            }
        } else {
            //normal operation
            //******* EDUARDO BEGIN **************//
            if (reqType == TOMMessageType.REQUEST) {
                //Reply to a normal request!
                if (response.getViewID() == getViewManager().getCurrentViewId()) {
                    ret = response.getContent(); // return the response
                } else {//if(response.getViewID() > getViewManager().getCurrentViewId())
                    //updated view received
                    reconfigureTo((View) TOMUtil.getObject(response.getContent()));

                    canSendLock.unlock();
                    return invoke(request, reqType);
                }
            } else {
                //Reply to a reconfigure request!
                Logger.println("Reconfiguration request' reply received!");
                //It is impossible to be less than...
                if (response.getViewID() > getViewManager().getCurrentViewId()) {
                    Object r = TOMUtil.getObject(response.getContent());
                    if (r instanceof View) { //did not executed the request because it is using an outdated view
                        reconfigureTo((View) r);

                        canSendLock.unlock();
                        return invoke(request, reqType);
                    } else { //reconfiguration executed!
                        reconfigureTo(((ReconfigureReply) r).getView());
                        ret = response.getContent();
                    }
                } else {
                    //Caso a reconfiguração nao foi executada porque algum parametro
                    // da requisição estava incorreto: o processo queria fazer algo que nao é permitido
                    ret = response.getContent();
                }
            }
        }
        //******* EDUARDO END **************//

        canSendLock.unlock();
        return ret;
    }

    //******* EDUARDO BEGIN **************//
    private void reconfigureTo(View v) {
        Logger.println("Installing a most up-to-date view with id=" + v.getId());
        getViewManager().reconfigureTo(v);
        replies = new TOMMessage[getViewManager().getCurrentViewN()];
        getCommunicationSystem().updateConnections();
    }
    //******* EDUARDO END **************//

    /**
     * This is the method invoked by the client side comunication system.
     *
     * @param reply The reply delivered by the client side comunication system
     */
    @Override
    public void replyReceived(TOMMessage reply) {
        canReceiveLock.lock();
        if (reqId == -1) {//no message being expected
            Logger.println("throwing out request: sender=" + reply.getSender() + " reqId=" + reply.getSequence());
            canReceiveLock.unlock();
            return;
        }

        int pos = getViewManager().getCurrentViewPos(reply.getSender());
        if (pos < 0) { //ignore messages that don't come from replicas
            canReceiveLock.unlock();
            return;
        }

        if (reply.getSequence() == reqId) {
            Logger.println("Receiving reply from " + reply.getSender() +
                    " with reqId:" + reply.getSequence() + ". Putting on pos=" + pos);

            if (replies[pos] == null) {
                receivedReplies++;
            }
            replies[pos] = reply;
            
            // Compare the reply just received, to the others
            int sameContent = 1;
            for (int i = 0; i < replies.length; i++) {
                if (i != pos && replies[i] != null && 
                        (comparator.compare(replies[i].getContent(), reply.getContent()) == 0)) {
                    sameContent++;
                    if (sameContent >= replyQuorum) {
                        response = extractor.extractResponse(replies, sameContent, pos);
                        reqId = -1;
                        this.sm.release(); // resumes the thread that is executing the "invoke" method
                        break;
                    }
                }
            }

            if (response == null
                    && receivedReplies >= getViewManager().getCurrentViewN() - getViewManager().getCurrentViewF()) {
                //it's not safe to wait for more replies (n-f replies received),
                //but there is no response available...
                reqId = -1;
                this.sm.release(); // resumes the thread that is executing the "invoke" method
            }
        } 

        // Critical section ends here. The semaphore can be released
        canReceiveLock.unlock();
    }
}
