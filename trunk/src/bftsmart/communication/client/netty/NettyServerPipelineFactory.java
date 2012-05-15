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

import java.util.HashMap;
import static org.jboss.netty.channel.Channels.pipeline;

import java.util.Hashtable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.SecretKey;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;

import bftsmart.reconfiguration.ServerViewManager;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev: 643 $, $Date: 2009/09/08 00:11:57 $
 */
public class NettyServerPipelineFactory implements ChannelPipelineFactory {

    NettyClientServerCommunicationSystemServerSide ncs;
    boolean isClient;
    HashMap sessionTable;
    SecretKey authKey;
    int macLength;
    int signatureLength;
    ServerViewManager manager;
    ReentrantReadWriteLock rl;
    ReentrantLock lock;

    public NettyServerPipelineFactory(NettyClientServerCommunicationSystemServerSide ncs, boolean isClient, HashMap sessionTable, SecretKey authKey, int macLength, ServerViewManager manager, ReentrantReadWriteLock rl, int signatureLength, ReentrantLock lock) {
        this.ncs = ncs;
        this.isClient = isClient;
        this.sessionTable = sessionTable;
        this.authKey = authKey;
        this.macLength = macLength;
        this.signatureLength = signatureLength;
        this.manager = manager;
        this.rl = rl;
        this.lock = lock;
    }


    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline();

        //******* EDUARDO BEGIN **************//
        p.addLast("decoder", new NettyTOMMessageDecoder(isClient, sessionTable,
                authKey, macLength,manager,rl,signatureLength,manager.getStaticConf().getUseMACs()==1?true:false));
        p.addLast("encoder", new NettyTOMMessageEncoder(isClient, sessionTable, macLength,rl,signatureLength, manager.getStaticConf().getUseMACs()==1?true:false));
        //******* EDUARDO END **************//

        p.addLast("handler", ncs);

        return p;
    }
}
