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
package bftsmart.reconfiguration;

import bftsmart.communication.CommunicationFactory;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.ViewStorage;
import bftsmart.tom.ServiceProxy;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.util.io.StateCodecs;
import bftsmart.tom.util.KeyLoader;
import bftsmart.tom.util.TOMUtil;

/**
 *
 * @author eduardo
 */
public class Reconfiguration {

    private ReconfigureRequest request;
    private ServiceProxy proxy;
    private int id;

    private KeyLoader keyLoader;
    private String configDir;

    // Programmatic (per-group / file-less) reconfiguration: when {@code conf} is set the
    // proxy is built from it (and its transport/view storage) instead of from disk.
    private TOMConfiguration conf;
    private CommunicationFactory communicationFactory;
    private ViewStorage viewStore;

    public Reconfiguration(int id, String configDir, KeyLoader loader) {
        this.id = id;
        this.keyLoader = loader;
        this.configDir = configDir;
    }

    /**
     * Constructor for programmatic, per-group reconfiguration. The reconfiguration client
     * is built from the given group {@link TOMConfiguration} (its ports/keys/view) and talks
     * to that group's replicas over the supplied transport, independently of any other group.
     *
     * @param conf the target group's configuration (its processId is used as the reconfiguration sender)
     * @param communicationFactory the transport factory used to reach that group's replicas
     * @param viewStore per-instance view storage so groups never collide on a shared view ({@code null} uses the default)
     */
    public Reconfiguration(TOMConfiguration conf, CommunicationFactory communicationFactory, ViewStorage viewStore) {
        this.conf = conf;
        this.id = conf.getProcessId();
        this.communicationFactory = communicationFactory;
        this.viewStore = viewStore;
    }

    public void connect(){
        if(proxy == null){
            if (conf != null) {
                proxy = new ServiceProxy(conf, null, null, communicationFactory, viewStore);
            } else {
                proxy = new ServiceProxy(id, configDir, null, null, keyLoader);
            }
        }
    }
    
    public void addVoter(int id, String ip, int port, int portRR){
    	this.setReconfiguration(
        			ServerViewController.ADD_SERVER,
        			id + ":" +
        			ip + ":" +
        			port + ":" +
        			portRR);
    }

    /** @deprecated use {@link #addVoter(int, String, int, int)}. */
    @Deprecated
    public void addServer(int id, String ip, int port, int portRR){
        addVoter(id, ip, port, portRR);
    }
    
    public void removeServer(int id){
        this.setReconfiguration(ServerViewController.REMOVE_SERVER, String.valueOf(id));
    }

    /**
     * Adds a non-voting member (listener) to the group. Like {@link #addServer}, the
     * address must also be present in config/hosts.config of the existing replicas.
     */
    public void addListener(int id, String ip, int port, int portRR){
        this.setReconfiguration(
                ServerViewController.ADD_LISTENER,
                id + ":" + ip + ":" + port + ":" + portRR);
    }

    /**
     * Promotes an existing listener to a voter (it starts participating in consensus).
     */
    public void promoteToVoter(int id){
        this.setReconfiguration(ServerViewController.PROMOTE_TO_VOTER, String.valueOf(id));
    }

    /**
     * Demotes an existing voter to a listener (stops voting, keeps replicating state).
     */
    public void demoteToListener(int id){
        this.setReconfiguration(ServerViewController.DEMOTE_TO_LISTENER, String.valueOf(id));
    }


    public void setF(int f){
      this.setReconfiguration(ServerViewController.CHANGE_F,String.valueOf(f));  
    }
    
    
    public void setReconfiguration(int prop, String value){
        if(request == null){
            //request = new ReconfigureRequest(proxy.getViewManager().getStaticConf().getProcessId());
            request = new ReconfigureRequest(id);
        }
        request.setProperty(prop, value);
    }
    
    public ReconfigureReply execute(){
        byte[] signature = TOMUtil.signMessage(proxy.getViewManager().getStaticConf().getPrivateKey(),
                request.toString().getBytes());
        request.setSignature(signature);
        byte[] reply = proxy.invoke(StateCodecs.reconfigureRequestToBytes(request), TOMMessageType.RECONFIG);
        request = null;
        return (ReconfigureReply) StateCodecs.reconfigReplyContentFromBytes(reply);
    }
    
    
    public void close(){
        proxy.close();
        proxy = null;
    }
    
}
