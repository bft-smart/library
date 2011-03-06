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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.communication.TOMMessageHandler;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.consensus.ConsensusServiceFactory;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.ShutdownThread;
import navigators.smart.tom.util.TOMConfiguration;

/**
 * This class is used to
 * assemble a total order messaging layer
 *
 */
public abstract class TOMReceiver implements TOMRequestReceiver {

    private boolean tomStackCreated = false;

    protected final TOMConfiguration conf;

    protected ServerCommunicationSystem cs = null; // Server side comunication system

    public TOMReceiver( TOMConfiguration conf) throws IOException {
        this.conf = conf;
        init(conf);
    }


    
    /**
     * This method initializes the object
     * 
     * TODO merge with ServiceReplica
     *
     * @param conf Total order messaging configuration
     * @throws IOException Is thrown when the init of the Com System fails
     */
    protected void init(TOMConfiguration conf) throws IOException {
        if (tomStackCreated) { // if this object was already initialized, don't do it again
            return;
        }

        cs = getCommunicationSystem();

        TOMLayer tomLayer = new TOMLayer( this, cs, conf);

        TOMMessageHandler msghndlr = new TOMMessageHandler(tomLayer);
        cs.addMessageHandler(SystemMessage.Type.FORWARDED,msghndlr);
        cs.addMessageHandler(SystemMessage.Type.SM_MSG,msghndlr);
        cs.setRequestReceiver(tomLayer);
        cs.start();

        ConsensusServiceFactory factory = createFactory(cs, conf);

        ConsensusService service = factory.newInstance(tomLayer);
        tomLayer.setConsensusService(service); //set backlink
        service.start();
        Runtime.getRuntime().addShutdownHook(new ShutdownThread(cs,service,tomLayer));

        tomStackCreated = true;
    }

    protected ServerCommunicationSystem getCommunicationSystem() throws IOException{
        return new ServerCommunicationSystem(conf);
    }

    @SuppressWarnings("unchecked")
    protected ConsensusServiceFactory createFactory(ServerCommunicationSystem cs, TOMConfiguration conf){
        String algorithm = conf.getConsensusAlgorithmFactory();
        Class<ConsensusServiceFactory> serviceclass;
        try {
            serviceclass = (Class<ConsensusServiceFactory>) Class.forName(algorithm);
            Object[] initargs = new Object[2];
            initargs[0] = cs;
            initargs[1] = conf;
            ConsensusServiceFactory factory = (ConsensusServiceFactory) serviceclass.getConstructors()[0].newInstance(initargs);
            return factory;
        } catch (InstantiationException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, "Failed to load ConsensusServiceFactory: "+algorithm, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TOMReceiver.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}

