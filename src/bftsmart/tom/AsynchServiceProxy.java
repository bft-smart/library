package bftsmart.tom;

import bftsmart.communication.client.ReplyListener;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.TOMUtil;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is an extension of 'ServiceProxy' that can waits for replies
 * asynchronously.
 *
 */
public class AsynchServiceProxy extends ServiceProxy {
    
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private HashMap<Integer, RequestContext> requestsContext;
    private HashMap<Integer, TOMMessage[]> requestsReplies;
    private HashMap<Integer, Integer> requestsAlias;
    private HashSet<Integer> requestsAcked;
    private ReentrantLock ackLock;
    private Condition gotAcked;
    private LinkedBlockingQueue<Integer> cleanQueue;
    private Thread cleanerThread;
    private LinkedBlockingQueue<RequestContext> invokeQueue;
    private Thread invokeThread;
    private boolean doWork = true;
    
/**
     * Constructor
     *
     * @see bellow
     */
    public AsynchServiceProxy(int processId) {
        this(processId, null);
    }

/**
     * Constructor
     *
     * @see bellow
     */
    public AsynchServiceProxy(int processId, String configHome) {
        super(processId, configHome);
        init();
    }
    
    /**
     * Constructor
     *
     * @see bellow
     */
    public AsynchServiceProxy(int processId, String configHome, KeyLoader loader) {
        super(processId, configHome, loader);
        init();
    }
    
    /**
     * Constructor
     *
     * @param processId Process id for this client (should be different from replicas)
     * @param configHome Configuration directory for BFT-SMART
     * @param replyComparator Used for comparing replies from different servers
     *                        to extract one returned by f+1
     * @param replyExtractor Used for extracting the response from the matching
     *                       quorum of replies
     * @param loader Used to load signature keys from disk
     */
    public AsynchServiceProxy(int processId, String configHome,
            Comparator<byte[]> replyComparator, Extractor replyExtractor, KeyLoader loader) {
        
        super(processId, configHome, replyComparator, replyExtractor, loader);
        init();
    }

    private void init() {
        requestsContext = new HashMap<>();
        requestsReplies = new HashMap<>();
        requestsAlias = new HashMap<>();
        requestsAcked = new HashSet<>();
        ackLock = new ReentrantLock();
        gotAcked =ackLock.newCondition();
        
        cleanQueue = new LinkedBlockingQueue<>();
        cleanerThread = new Thread() {
            
            public void run() {
                
                while (doWork) {
                    
                    try {
                        
                        if (cleanQueue.poll(200, TimeUnit.MILLISECONDS) == null) continue;
                        
                        int requestId = cleanQueue.take();
                        
                        Integer id = requestId;

                        do {
                            
                            ackLock.lock();

                            while (!requestsAcked.contains(id)) {
                                try {
                                    gotAcked.await();
                                } catch (InterruptedException ex) {
                                    logger.error("Interruption error.",ex);
                                }
                            }

                            ackLock.unlock();

                            requestsAcked.remove(id);
                        
                            requestsContext.remove(id);
                            requestsReplies.remove(id);

                            id = requestsAlias.remove(id);

                        } while (id != null);
        
                    } catch (InterruptedException ex) {
                        
                        logger.error("Interrupted error.",ex);
                    }
                }
            }
        };
        
        cleanerThread.start();
        
        invokeQueue = new LinkedBlockingQueue<>();
        invokeThread = new Thread() {
            
            public void run() {
                
                while(doWork) {
                    
                    try {
                        RequestContext requestContext = null;
                        
                        requestContext = invokeQueue.poll(200, TimeUnit.MILLISECONDS);
                        
                        if (requestContext == null) continue;
                        
                        logger.debug("Dequeued invoke at client {} for request #{}", getViewManager().getStaticConf().getProcessId(), requestContext.getOperationId());

                        invokeAsynch(requestContext);

                        logger.debug("Finished invoke at client {} for request #{}", getViewManager().getStaticConf().getProcessId(), requestContext.getOperationId());

                        
                    } catch (InterruptedException ex) {
                        logger.error("Interrupted error.",ex);
                    }
                }
            }
        };
        
        invokeThread.start();
    }
    
    private View newView(byte[] bytes) {
        
        Object o = TOMUtil.getObject(bytes);
        return (o != null && o instanceof View ? (View) o : null);
    }
    /**
     * @see bellow
     */
    public int invokeAsynchRequest(byte[] request, ReplyListener replyListener, TOMMessageType reqType) throws InterruptedException {
        return invokeAsynchRequest(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType, false);
    }

    /**
     * @see bellow
     */
    public int invokeAsynchRequest(byte[] request, ReplyListener replyListener, TOMMessageType reqType, boolean dos) throws InterruptedException {
        return invokeAsynchRequest(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType, dos);
    }
    
    /**
     * This method asynchronously sends a request to the replicas.
     * It returns immediately after adding the request to an internal queue, hence preventing the current thread from blocking while waiting for a quorum of ACKS.
     * After instantiation, developers must either always use one of the 'invokeAsynchRequest' or always the 'invokeAsynch' method.
     * 
     * @param request Request to be sent
     * @param targets The IDs for the replicas to which to send the request
     * @param replyListener Callback object that handles reception of replies
     * @param reqType Request type
     * @param dos Ignore control flow mechanism
     * 
     * @return A unique identification for the request
     */
    public int invokeAsynchRequest(byte[] request, int[] targets, ReplyListener replyListener, TOMMessageType reqType, boolean dos) throws InterruptedException {
                
         RequestContext requestContext = generateNextContext(request, targets, replyListener, reqType, dos);
        
         logger.debug("Enqueuing invoke at client {} for request #{}", getViewManager().getStaticConf().getProcessId(), requestContext.getOperationId());

        invokeQueue.put(requestContext);
                            
        return requestContext.getOperationId();
    }

    /**
     * @see bellow
     */
    public RequestContext generateNextContext(byte[] request, ReplyListener replyListener, TOMMessageType reqType) throws InterruptedException {
        return generateNextContext(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType, false, System.nanoTime());
    }

    /**
     * @see bellow
     */
    public RequestContext generateNextContext(byte[] request, ReplyListener replyListener, TOMMessageType reqType, boolean dos) throws InterruptedException {
        return generateNextContext(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType, dos, System.nanoTime());
    }
    
    
    /**
     * @see bellow
     */
    public RequestContext generateNextContext(byte[] request, int[] targets, ReplyListener replyListener, TOMMessageType reqType, boolean dos) {
        
        return generateNextContext(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType, false, System.nanoTime());
    }
    
    /**
     * Generate the next request context. This method should only be used if request are to be submitted using 'invokeAsych'.
     * 
     * @param request Request to be sent
     * @param targets The IDs for the replicas to which to send the request
     * @param replyListener Callback object that handles reception of replies
     * @param reqType Request type
     * @param dos Ignore control flow mechanism
     * @param sentTime Timestamp of the time of creating
     * 
     * @return A unique identification for the request
     */
    public RequestContext generateNextContext(byte[] request, int[] targets, ReplyListener replyListener, TOMMessageType reqType, boolean dos, long sentTime) {
        
        return new RequestContext(generateRequestId(reqType), generateOperationId(),
                reqType, targets, sentTime, replyListener, request, dos);
    }
    
    /**
     * Purges all information associated to the request.
     * This should always be invoked once enough replies are received and processed by the ReplyListener callback.
     * 
     * @param requestId A unique identification for a previously sent request
     */
    public void cleanAsynchRequest(int requestId) throws InterruptedException {
        
        cleanQueue.put(requestId);

    }

    @Override
    public void close() {
        doWork = false;
        super.close();
    }
    /**
     * This is the method invoked by the client side communication system.
     *
     * @param reply The reply delivered by the client side communication system
     */
    @Override
    public void replyReceived(TOMMessage reply) {
        logger.debug("Asynchronously received reply from " + reply.getSender() + " with sequence number " + reply.getSequence() + " and operation ID " + reply.getOperationId());

        try {
            canReceiveLock.lock();

            RequestContext requestContext = requestsContext.get(reply.getOperationId());

            if (requestContext == null) { // it is not a asynchronous request                
                super.replyReceived(reply);
                return;
            }
            
            //control flow mechanism
            if (reply.getReqType() == TOMMessageType.ACK) {

                logger.debug("Received ACK from {} to {}",reply.getSender(), getProcessId());

                if (reply.getSession() == getSession() && ackId == requestContext.getOperationId() &&
                        reply.getOperationId() == requestContext.getOperationId() && reply.getSequence() == requestContext.getReqId()) {

                    logger.debug("ACK is for the current request ({})", requestContext.getOperationId());
                    
                    int pos = getViewManager().getCurrentViewPos(reply.getSender());
                    
                    int sameContent = 1;
                    int leader = -1;
                    int ackSeq = -1;

                    acks[pos] = reply;

                    for (int i = 0; i < acks.length; i++) {

                        if ((i != pos || getViewManager().getCurrentViewN() == 1) && acks[i] != null
                                        && (comparator.compare(acks[i].getContent(), reply.getContent()) == 0)) {
                                sameContent++;
                                if (sameContent >= getReplyQuorum()) {
                                        ByteBuffer buff = ByteBuffer.wrap(extractor.extractResponse(acks, sameContent, pos).getContent());
                                        
                                        ackSeq = buff.getInt();
                                        leader = buff.getInt();

                                        logger.debug("Client {} received quorum of ACKs for req id #{} "+
                                                  "with ACK sequence {} indicating replica {} as the leader", 
                                                    getProcessId(), requestContext.getOperationId(), ackSeq,leader);

                                        int p = getViewManager().getCurrentViewPos(leader);

                                        if (this.ackSeq == ackSeq && p != -1 && acks[p] != null) {

                                            logger.debug("Client {} also received ACK from leader, client "+
                                                    "can stop re-transmiting request #{}", getProcessId(), requestContext.getOperationId());

                                            this.leader = leader;
                                            Arrays.fill(acks, null);
                                            requestsAcked.add(ackId);
                                            ackId = -1;
                                            
                                            this.controlFlow.release();
                                            ackLock.lock();
                                            gotAcked.signalAll();
                                            ackLock.unlock();
                                        }
                                }
                        }
                    }
                }

                //canReceiveLock.unlock();

                return;
            }

            if (contains(requestContext.getTargets(), reply.getSender())
                    && (reply.getSequence() == requestContext.getReqId())
                    //&& (reply.getOperationId() == requestContext.getOperationId())
                    && (reply.getReqType().compareTo(requestContext.getRequestType())) == 0) {

                logger.debug("Deliverying message from " + reply.getSender() + " with sequence number " + reply.getSequence() + " and operation ID " + reply.getOperationId() + " to the listener");

                ReplyListener replyListener = requestContext.getReplyListener();
                
                View v = null;

                if (replyListener != null) {

                    //if (reply.getViewID() > getViewManager().getCurrentViewId()) { // Deal with a system reconfiguration
                    if ((v = newView(reply.getContent())) != null && !requestsAlias.containsKey(reply.getOperationId())) { // Deal with a system reconfiguration
    
                        TOMMessage[] replies = requestsReplies.get(reply.getOperationId());

                        int sameContent = 1;
                        int replyQuorum = getReplyQuorum();

                        int pos = getViewManager().getCurrentViewPos(reply.getSender());

                        replies[pos] = reply;

                        for (int i = 0; i < replies.length; i++) {

                            if ((replies[i] != null) && (i != pos || getViewManager().getCurrentViewN() == 1)
                                    && (reply.getReqType() != TOMMessageType.ORDERED_REQUEST || Arrays.equals(replies[i].getContent(), reply.getContent()))) {
                                sameContent++;
                            }
                        }
                        
                        if (sameContent >= replyQuorum) {

                            if (v.getId() > getViewManager().getCurrentViewId()) {

                                reconfigureTo(v);
                            }

                            requestContext.getReplyListener().reset();

                            /*Thread t = new Thread() {

                                @Override
                                public void run() {

                                    //int id = invokeAsynch(requestContext.getRequest(), requestContext.getTargets(), requestContext.getReplyListener(), TOMMessageType.ORDERED_REQUEST, false);
                            
                                    //requestsAlias.put(reply.getOperationId(), id);
                            
                                    RequestContext newContext = new RequestContext(generateRequestId(requestContext.getRequestType()), generateOperationId(),
                                        requestContext.getRequestType(), requestContext.getTargets(), System.currentTimeMillis(), replyListener, requestContext.getRequest(), requestContext.getDoS());
                            
                                    requestsAlias.put(reply.getOperationId(), newContext.getOperationId());
                                }

                            };

                            t.start();*/
                            
                            RequestContext newContext = new RequestContext(generateRequestId(requestContext.getRequestType()), generateOperationId(),
                                requestContext.getRequestType(), requestContext.getTargets(), System.currentTimeMillis(), replyListener, requestContext.getRequest(), requestContext.getDoS());
                            invokeQueue.put(newContext);

                        }
                        
                        
                    } else if (!requestsAlias.containsKey(reply.getOperationId())) {
                            
                            requestContext.getReplyListener().replyReceived(requestContext, reply);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Error processing received request",ex);
        } finally {
            canReceiveLock.unlock();
        }
    }

    /**
     * This method asynchronously sends a request to the replicas using a RequestContext object.
     * It blocks while waiting for a quorum of ACKs from the replicas.
     * After instantiation, developers must either always use one of the 'invokeAsynchRequest' or always the 'invokeAsynch' method.
     * 
     * @param reqCtx The context for the request to be submitted.
     */
    public void invokeAsynch(RequestContext reqCtx) {

        logger.debug("Asynchronously sending request to " + Arrays.toString(reqCtx.getTargets()));

        canSendLock.lock();
        
        try {
            logger.debug("Storing request context for " + reqCtx.getOperationId());
            requestsContext.put(reqCtx.getOperationId(), reqCtx);
            requestsReplies.put(reqCtx.getOperationId(), new TOMMessage[super.getViewManager().getCurrentViewN()]);

            ackId = reqCtx.getOperationId();
            ackSeq = 0;
            
            Arrays.fill(acks, null);
            
            logger.debug("Sending invoke at client {} for request #{}", getViewManager().getStaticConf().getProcessId(), reqCtx.getOperationId());


            TOMMessage sm = new TOMMessage(getProcessId(), getSession(), reqCtx.getReqId(),
                    reqCtx.getOperationId(), reqCtx.getRequest(), getViewManager().getCurrentViewId(), reqCtx.getRequestType());
            
            //sendMessageToTargets(reqCtx.getRequest(), reqCtx.getReqId(),
            //        reqCtx.getOperationId(), reqCtx.getTargets(), reqCtx.getRequestType());
            
            sm.setAckSeq(ackSeq);
            
            //int[] targets = (leader != -1 ?  new int[]{leader} : getViewManager().getCurrentViewProcesses());
            
            TOMulticast(sm);
            
            //Control flow
            if (!reqCtx.getDoS() && getViewManager().getStaticConf().getControlFlow()) {
                while (true) {

                    if (this.controlFlow.tryAcquire(getViewManager().getStaticConf().getControlFlowTimeout(), TimeUnit.MILLISECONDS)) {

                        break;

                    } else {
                        
                        sm = new TOMMessage(getProcessId(), getSession(), reqCtx.getReqId(),
                            reqCtx.getOperationId(), reqCtx.getRequest(), getViewManager().getCurrentViewId(), reqCtx.getRequestType());
                        
                        ackSeq++;
                        sm.setAckSeq(ackSeq);

                        logger.warn("Retrying invoke at client {} for request #{} with ACK sequence #{}", 
                                getViewManager().getStaticConf().getProcessId(), reqCtx.getOperationId(), sm.getAckSeq());
                        Arrays.fill(acks, null);
                        
                        TOMulticast(sm);
                    }
                }
            }

        } catch (InterruptedException ex) {
            logger.error("Problem aquiring semaphore",ex);
        } finally {
            canSendLock.unlock();
        }

        //return requestContext.getOperationId();
    }

    /**
     *
     * @param targets
     * @param senderId
     * @return
     */
    private boolean contains(int[] targets, int senderId) {
        for (int i = 0; i < targets.length; i++) {
            if (targets[i] == senderId) {
                return true;
            }
        }
        return false;
    }

}
