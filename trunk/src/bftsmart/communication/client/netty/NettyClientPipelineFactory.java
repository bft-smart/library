/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.communication.client.netty;

import static org.jboss.netty.channel.Channels.pipeline;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.crypto.SecretKey;


import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;

import bftsmart.reconfiguration.ClientViewController;


/**
 * @author The Netty Project (netty-dev@lists.jboss.org)
 * @author Trustin Lee (tlee@redhat.com)
 * @version $Rev: 643 $, $Date: 2009/09/08 00:11:57 $
 */
public class NettyClientPipelineFactory implements ChannelPipelineFactory {

    NettyClientServerCommunicationSystemClientSide ncs;
    Map sessionTable;
    int macLength;
    int signatureLength;

    //******* EDUARDO BEGIN **************//
    ClientViewController controller;
    //******* EDUARDO END **************//

    ReentrantReadWriteLock rl;

    public NettyClientPipelineFactory(NettyClientServerCommunicationSystemClientSide ncs, Map sessionTable, int macLength, ClientViewController controller, ReentrantReadWriteLock rl, int signatureLength) {
        this.ncs = ncs;
        this.sessionTable = sessionTable;
        this.macLength = macLength;
        this.signatureLength = signatureLength;
        this.rl = rl;
        this.controller = controller;
    }


    @Override
    public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline p = pipeline();
        p.addLast("decoder", new NettyTOMMessageDecoder(true, sessionTable, macLength,controller,rl,signatureLength,controller.getStaticConf().getUseMACs()==1?true:false));
        p.addLast("encoder", new NettyTOMMessageEncoder(true, sessionTable, macLength,rl, signatureLength, controller.getStaticConf().getUseMACs()==1?true:false));
        p.addLast("handler", ncs);

        return p;
    }
}
