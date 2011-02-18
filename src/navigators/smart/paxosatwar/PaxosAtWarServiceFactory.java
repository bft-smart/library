/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.paxosatwar;

import navigators.smart.communication.ServerCommunicationSystem;
import navigators.smart.consensus.ConsensusService;
import navigators.smart.consensus.ConsensusServiceFactory;
import navigators.smart.paxosatwar.executionmanager.ExecutionManager;
import navigators.smart.paxosatwar.executionmanager.LeaderModule;
import navigators.smart.paxosatwar.executionmanager.ProofVerifier;
import navigators.smart.paxosatwar.messages.MessageFactory;
import navigators.smart.paxosatwar.messages.PaWMessageHandler;
import navigators.smart.paxosatwar.requesthandler.RequestHandler;
import navigators.smart.paxosatwar.roles.Acceptor;
import navigators.smart.paxosatwar.roles.Proposer;
import navigators.smart.tom.core.TOMLayer;
import navigators.smart.tom.core.messages.SystemMessage;
import navigators.smart.tom.util.TOMConfiguration;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class PaxosAtWarServiceFactory implements ConsensusServiceFactory{
    
    /** Holds the configuration */
    private final TOMConfiguration conf;

    /** Holds a link to the server communication system*/
    private final ServerCommunicationSystem cs;

    /**
     * Creates a new Instance with the given Configuration and communication system
     * @param cs The communicationsystem
     * @param conf The configuration
     */
    public PaxosAtWarServiceFactory(ServerCommunicationSystem cs, TOMConfiguration conf){
        this.cs = cs;
        this.conf = conf;
    }

    public ConsensusService newInstance(TOMLayer tom) {

         // Get group of replicas
        int[] group = new int[conf.getN()];
        for (int i = 0; i < group.length; i++) {
            group[i] = i;
        }

        int me = conf.getProcessId(); // this process ID

        if (me >= group.length) {
            throw new RuntimeException("I'm not an acceptor!");
        }
         // Init the PaW Service and al its components
        MessageFactory messageFactory = new MessageFactory(me);
        ProofVerifier proofVerifier = new ProofVerifier(conf);
        //init leaderhandling
        LeaderModule lm = new LeaderModule();

        Acceptor acceptor = new Acceptor(cs, messageFactory, proofVerifier, lm, conf,tom);
        Proposer proposer = new Proposer(cs, messageFactory, proofVerifier, conf);

        ExecutionManager manager = new ExecutionManager(acceptor, proposer,
                group, conf.getF(), me, conf.getFreezeInitialTimeout(),tom,lm);
        RequestHandler req = new RequestHandler(cs, manager, lm, proofVerifier, conf,tom);
        req.start();
        //init message handling threads
        //set backlinks
        acceptor.setManager(manager);
        acceptor.setRequesthandler(req);
        proposer.setManager(manager);
        manager.setRequestHandler(req);

        PaWMessageHandler msghandler = new PaWMessageHandler(acceptor, proposer, req);
        //create service object that implements ConsensusService interface
        ConsensusService service = new PaxosAtWarService(lm, manager,msghandler);
        cs.addMessageHandler(SystemMessage.Type.PAXOS_MSG, msghandler);
        return service;
    }

}
