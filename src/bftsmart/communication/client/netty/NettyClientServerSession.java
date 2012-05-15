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

package bftsmart.communication.client.netty;

import java.security.PublicKey;
import java.util.concurrent.locks.Lock;

import javax.crypto.Mac;

import org.jboss.netty.channel.Channel;

/**
 *
 * @author Paulo Sousa
 */
public class NettyClientServerSession {
    private Channel channel;
    private Mac macSend;
    private Mac macReceive;
    private int replicaId;
    private PublicKey pKey;
    private Lock lock;
    private int lastMsgReceived;

    public NettyClientServerSession(Channel channel, Mac macSend, Mac macReceive, int replicaId, PublicKey pKey, Lock lock) {
        this.channel = channel;
        this.macSend = macSend;
        this.macReceive = macReceive;
        this.replicaId = replicaId;
        this.pKey = pKey;
        this.lock = lock;
        this.lastMsgReceived = -1;
    }
    

    public Mac getMacReceive() {
        return macReceive;
    }


    public Mac getMacSend() {
        return macSend;
    }


    public Channel getChannel() {
        return channel;
    }

    public int getReplicaId() {
        return replicaId;
    }

    public PublicKey getPublicKey(){
        return pKey;
    }

    public Lock getLock(){
        return lock;
    }

    public int getLastMsgReceived(){
        return lastMsgReceived;
    }

    public void setLastMsgReceived(int lastMsgReceived_){
        this.lastMsgReceived = lastMsgReceived_;
    }

}
