package bftsmart.tom;

import bftsmart.communication.client.ReplyListener;
import bftsmart.tom.core.messages.TOMMessageType;

/**
 * This class contains information related to a client request.
 */
public final class RequestContext{

	private final int reqId;
	private final int operationId;
	private final TOMMessageType requestType;
	private final int [] targets;
	private final long sendingTime;
	private final ReplyListener replyListener;
        private final byte[] request;
        

        /**
         * Constructor 
         * 
         * @param reqId Request ID
         * @param operationId Operation ID
         * @param requestType The request type
         * @param targets IDs of the targets to which the request was sent
         * @param sendingTime Sending time
         * @param replyListener Reply listener associated to the request
         * @param request Payload of the request
         */
	public RequestContext(int reqId, int operationId, TOMMessageType requestType, int [] targets, 
			long sendingTime, ReplyListener replyListener, byte[] request) {
		this.reqId = reqId;
		this.operationId = operationId;
		this.requestType = requestType;
		this.targets = targets;
		this.sendingTime = sendingTime;
		this.replyListener = replyListener;
                this.request = request;
	}
        
        /**
         * Returns the request unique ID
         * 
         * @return Request ID
         */
	public final int getReqId() {
		return reqId;
	}
        
        /**
         * Returns the operation ID
         * 
         * @return Operation ID
         */
	public  final int getOperationId() {
		return operationId;
	}
        
        /**
         * Returns the request type
         * @return The request type
         */
	public final TOMMessageType getRequestType() {
		return requestType;
	}
        
        /**
         * Returns the sending time
         * @return Sending time
         */
	public  final long getSendingTime() {
		return sendingTime;
	}
        
        /**
         * Returns the reply listener associated to the request
         * @return Reply listener associated to the request
         */
	public ReplyListener getReplyListener(){
		return replyListener;
	}
        
        /**
         * Returns the IDs of the targets to which the request was sent
         * @return IDs of the targets to which the request was sent
         */
	public int [] getTargets() {
		return targets;
	}
        
        /**
         * The payload of the request
         * @return Payload of the request
         */
        public byte [] getRequest() {
		return request;
	}
}

