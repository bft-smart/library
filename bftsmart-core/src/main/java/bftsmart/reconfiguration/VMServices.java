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

import bftsmart.tom.util.KeyLoader;

/**
 * This class is used by the trusted client to add and remove replicas from the group
 */

public class VMServices {
    
    private KeyLoader keyLoader;
    private String configDir;
    
    /**
     * Constructor. It adopts the default RSA key loader and default configuration path.
     */
    public VMServices() { // for the default keyloader and provider
        
        keyLoader = null;
        configDir = "";
    }
    
    /**
     * Constructor.
     * 
     * @param keyLoader Key loader to use to fetch keys from disk
     * @param configDir Configuration path
     */
    public VMServices(KeyLoader keyLoader, String configDir) {
        
        this.keyLoader = keyLoader;
        this.configDir = configDir;
    }
    
    /**
     * Adds a new server to the group
     * 
     * @param id ID of the server to be added (needs to match the value in config/hosts.config)
     * @param ipAddress IP address of the server to be added (needs to match the value in config/hosts.config)
     * @param port Port of the server to be added (needs to match the value in config/hosts.config)
     */
    public void addServer(int id, String ipAddress, int port, int portRR) {
        
        ViewManager viewManager = new ViewManager(configDir, keyLoader);
        
        viewManager.addServer(id, ipAddress, port, portRR);
        
        execute(viewManager);

    }
    
    /**
     * Removes a server from the group
     * 
     * @param id ID of the server to be removed 
     */
    public void removeServer (int id) {

        ViewManager viewManager = new ViewManager(keyLoader);

        viewManager.removeServer(id);

        execute(viewManager);

    }

    /**
     * Adds a non-voting member (listener) to the group. The listener replicates state
     * but does not participate in consensus until it is promoted.
     *
     * @param id Id of the listener to add (must match config/hosts.config)
     * @param ipAddress Address of the listener
     * @param port Client port of the listener
     * @param portRR Server-to-server port of the listener
     */
    public void addListener(int id, String ipAddress, int port, int portRR) {
        ViewManager viewManager = new ViewManager(configDir, keyLoader);
        viewManager.addListener(id, ipAddress, port, portRR);
        execute(viewManager);
    }

    /**
     * Promotes an existing listener to a voter (it starts participating in consensus).
     *
     * @param id Id of the listener to promote
     */
    public void promoteToVoter(int id) {
        ViewManager viewManager = new ViewManager(configDir, keyLoader);
        viewManager.promoteToVoter(id);
        execute(viewManager);
    }

    /**
     * Demotes an existing voter to a listener (it stops voting but keeps replicating state).
     *
     * @param id Id of the voter to demote
     */
    public void demoteToListener(int id) {
        ViewManager viewManager = new ViewManager(configDir, keyLoader);
        viewManager.demoteToListener(id);
        execute(viewManager);
    }
    
    private void execute(ViewManager viewManager) {
        
        viewManager.executeUpdates();
        
        viewManager.close();
    }
}