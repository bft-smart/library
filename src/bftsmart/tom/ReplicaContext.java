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
package bftsmart.tom;

import bftsmart.communication.ServerCommunicationSystem;
import bftsmart.reconfiguration.ServerViewManager;
import bftsmart.reconfiguration.util.TOMConfiguration;
import bftsmart.reconfiguration.views.View;

/**
 *
 * @author alysson
 */
public class ReplicaContext {
    
    private ServerCommunicationSystem cs; // Server side comunication system
    private ServerViewManager SVManager;

    public ReplicaContext(ServerCommunicationSystem cs, 
                                 ServerViewManager SVManager) {
        this.cs = cs;
        this.SVManager = SVManager;
    }
    
    //TODO: implement a method that allow the replica to send a message with
    //total order to all other replicas
       
    /**
     * Returns the static configuration of this replica.
     * 
     * @return the static configuration of this replica
     */
    public TOMConfiguration getStaticConfiguration() {
        return SVManager.getStaticConf();
    }
    
    /**
     * Returns the current view of the replica group.
     * 
     * @return the current view of the replica group.
     */
    public View getCurrentView() {
        return SVManager.getCurrentView();
    }

	public ServerCommunicationSystem getServerCommunicationSystem() {
		return cs;
	}

	public void setServerCommunicationSystem(ServerCommunicationSystem cs) {
		this.cs = cs;
	}
}
