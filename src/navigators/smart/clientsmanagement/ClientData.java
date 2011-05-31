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

package navigators.smart.clientsmanagement;

import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.Logger;
import navigators.smart.tom.util.TOMUtil;

public class ClientData {

    ReentrantLock clientLock = new ReentrantLock();

    private int clientId;
    //private PublicKey publicKey = null;

    private int session = -1;

    private int lastMessageReceived = -1;
    private long lastMessageReceivedTime = 0;

    private int lastMessageExecuted = -1;

    private RequestList pendingRequests = new RequestList();
    //anb: new code to deal with client requests that arrive after their execution
    private RequestList orderedRequests = new RequestList(5);

    private Signature signatureVerificator = null;

    /**
     * Class constructor. Just store the clientId and creates a signature
     * verificator for a given client public key.
     *
     * @param clientId client unique id
     * @param publicKey client public key
     */
    public ClientData(int clientId, PublicKey publicKey) {
        this.clientId = clientId;
        if(publicKey != null) {
            try {
                signatureVerificator = Signature.getInstance("SHA1withRSA");
                signatureVerificator.initVerify(publicKey);
                Logger.println("Signature verifier initialized for client "+clientId);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    public int getClientId() {
        return clientId;
    }

    public int getSession() {
        return session;
    }

    public void setSession(int session) {
        this.session = session;
    }

    public RequestList getPendingRequests() {
        return pendingRequests;
    }

    public RequestList getOrderedRequests() {
        return orderedRequests;
    }

    public void setLastMessageExecuted(int lastMessageExecuted) {
        this.lastMessageExecuted = lastMessageExecuted;
    }

    public int getLastMessageExecuted() {
        return lastMessageExecuted;
    }

    public void setLastMessageReceived(int lastMessageReceived) {
        this.lastMessageReceived = lastMessageReceived;
    }

    public int getLastMessageReceived() {
        return lastMessageReceived;
    }

    public void setLastMessageReceivedTime(long lastMessageReceivedTime) {
        this.lastMessageReceivedTime = lastMessageReceivedTime;
    }

    public long getLastMessageReceivedTime() {
        return lastMessageReceivedTime;
    }

    public boolean verifySignature(byte[] message, byte[] signature) {
        if(signatureVerificator != null) {
            try {
                return TOMUtil.verifySignature(signatureVerificator, message, signature);
            } catch (SignatureException ex) {
                System.err.println("Error in processing client "+clientId+" signature: "+ex.getMessage());
            }
        }
        return false;
    }

    public boolean removeOrderedRequest(TOMMessage request) {
        if(pendingRequests.remove(request)) {
            //anb: new code to deal with client requests that arrive after their execution
            orderedRequests.addLast(request);
            return true;
        }
        return false;
    }

    public boolean removeRequest(TOMMessage request) {
	lastMessageExecuted = request.getSequence();
	boolean result = pendingRequests.remove(request);
        //anb: new code to deal with client requests that arrive after their execution
        orderedRequests.addLast(request);

	for(Iterator<TOMMessage> it = pendingRequests.iterator();it.hasNext();){
		TOMMessage msg = it.next();
		if(msg.getSequence()<request.getSequence()){
			it.remove();
		}
	}

    	return result;
    }

    public TOMMessage getReply(int reqId) {
        TOMMessage request = orderedRequests.getById(reqId);
        if(request != null) {
            return request.reply;
        } else {
            return null;
        }
    }

}
