package bftsmart.tom;

import bftsmart.communication.client.ReplyListener;
import bftsmart.reconfiguration.views.View;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.Extractor;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.TOMUtil;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

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

/**
     * Constructor
     *
     * @see bellow
     */
    public AsynchServiceProxy(int processId) {
        this(processId, null);
        init();
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
    }
    
    private View newView(byte[] bytes) {
        
        Object o = TOMUtil.getObject(bytes);
        return (o != null && o instanceof View ? (View) o : null);
    }
    /**
     * @see bellow
     */
    public int invokeAsynchRequest(byte[] request, ReplyListener replyListener, TOMMessageType reqType) {
        return invokeAsynchRequest(request, super.getViewManager().getCurrentViewProcesses(), replyListener, reqType);
    }

    /**
     * This method asynchronously sends a request to the replicas.
     * 
     * @param request Request to be sent
     * @param targets The IDs for the replicas to which to send the request
     * @param replyListener Callback object that handles reception of replies
     * @param reqType Request type
     * 
     * @return A unique identification for the request
     */
    public int invokeAsynchRequest(byte[] request, int[] targets, ReplyListener replyListener, TOMMessageType reqType) {
        return invokeAsynch(request, targets, replyListener, reqType);
    }

    /**
     * Purges all information associated to the request.
     * This should always be invoked once enough replies are received and processed by the ReplyListener callback.
     * 
     * @param requestId A unique identification for a previously sent request
     */
    public void cleanAsynchRequest(int requestId) {

        Integer id = requestId;

        do {

            requestsContext.remove(id);
            requestsReplies.remove(id);

            id = requestsAlias.remove(id);

        } while (id != null);

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

                            Thread t = new Thread() {

                                @Override
                                public void run() {

                                    int id = invokeAsynch(requestContext.getRequest(), requestContext.getTargets(), requestContext.getReplyListener(), TOMMessageType.ORDERED_REQUEST);

                                    requestsAlias.put(reply.getOperationId(), id);
                                }

                            };

                            t.start();

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

    private int invokeAsynch(byte[] request, int[] targets, ReplyListener replyListener, TOMMessageType reqType) {

        logger.debug("Asynchronously sending request to " + Arrays.toString(targets));

        RequestContext requestContext = null;

        canSendLock.lock();

        requestContext = new RequestContext(generateRequestId(reqType), generateOperationId(),
                reqType, targets, System.currentTimeMillis(), replyListener, request);

        try {
            logger.debug("Storing request context for " + requestContext.getOperationId());
            requestsContext.put(requestContext.getOperationId(), requestContext);
            requestsReplies.put(requestContext.getOperationId(), new TOMMessage[super.getViewManager().getCurrentViewN()]);

            sendMessageToTargets(request, requestContext.getReqId(), requestContext.getOperationId(), targets, reqType);

        } finally {
            canSendLock.unlock();
        }

        return requestContext.getOperationId();
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
