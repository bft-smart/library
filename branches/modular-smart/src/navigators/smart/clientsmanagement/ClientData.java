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

import java.util.Iterator;
import java.util.concurrent.locks.ReentrantLock;

import navigators.smart.reconfiguration.ReconfigurationManager;
import navigators.smart.tom.core.messages.TOMMessage;
import navigators.smart.tom.util.TOMUtil;

public class ClientData {

    ReentrantLock clientLock = new ReentrantLock();

    private int clientId;
    //private PublicKey publicKey = null;

    private int session = -1;

    private int lastMessageReceived = -1;
    private long lastMessageReceivedTime = 0;

    private int lastMessageExecuted = -1;

    private PendingRequests pendingRequests = new PendingRequests();

    private TOMMessage lastReplySent = null;

    private ReconfigurationManager manager;
    /**
     * Class constructor. Just store the clientId.
     *
     * @param clientId
     */
    public ClientData(int clientId, ReconfigurationManager manager) {
        this.clientId = clientId;
        this.manager = manager;
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

    public PendingRequests getPendingRequests() {
        return pendingRequests;
    }

    /*public PublicKey getPublicKey() {
        if(publicKey == null) {
            publicKey = TOMConfiguration.getRSAPublicKey(clientId);
        }

        return publicKey;
    }*/

    public boolean verifySignature(byte[] message, byte[] signature) {
        //******* EDUARDO BEGIN **************//

        return TOMUtil.verifySignature(manager.getStaticConf().getRSAPublicKey(clientId),
                message, signature);

        //******* EDUARDO END **************//
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

    public void setLastReplySent(TOMMessage lastReplySent) {
        this.lastReplySent = lastReplySent;
    }

    public TOMMessage getLastReplySent() {
        return lastReplySent;
    }

    /* JOAO BEGIN: PARA TRATAR DE PEDIDOS QUE NAO VAO SER PROCESSADOS */

    /* Este metodo foi criado pelo christian, mas usa um atributo que neste codigo nao existe,
       por isso as partes do codigo que fazem uso dele estao comentadas por mim */
    public boolean removeRequest(TOMMessage request) {
	lastMessageExecuted = request.getSequence();
	boolean result = pendingRequests.remove(request) /*||
            proposedRequests.remove(request)*/;
	//remove outdated messages from this client
	for(Iterator<TOMMessage> it = pendingRequests.iterator();it.hasNext();){
		TOMMessage msg = it.next();
		if(msg.getSequence()<request.getSequence()){
			it.remove();
		}
	}
	/*for(Iterator<TOMMessage> it = proposedRequests.iterator();it.hasNext();){
		TOMMessage msg = it.next();
		if(msg.getSequence()<request.getSequence()){
			it.remove();
		}
	}*/
    	return result;
    }
    /* JOAO END: PARA TRATAR DE PEDIDOS QUE NAO VAO SER PROCESSADOS */

}
