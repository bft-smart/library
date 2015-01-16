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
package bftsmart.tom.server;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.ReplicaContext;

/**
 * 
 * @author Marcel Santos
 *
 */
public interface Recoverable {
	
	public void setReplicaContext(ReplicaContext replicaContext);
	
    /**
     * 
     * This  method should return a representation of the application state
     * @param eid Execution up to which the application should return an Application state
     * @param sendState true if the replica should send a complete
     * representation of the state instead of only the hash. False otherwise
     * @return  A representation of the application state
     */
    public ApplicationState getState(int eid, boolean sendState);
    
    /**
     * Sets the state to the representation obtained in the state transfer protocol
     * @param eid Execution up to which the state is complete
     * @param state State obtained in the state transfer protocol
     * @return 
     */
    public int setState(ApplicationState state);
    
    /**
     * Recoverers implementing this interface will have to chose among
     * different options of state managers like DurableStateManager or
     * StandardStateManager. The recoverer class can also define a new
     * strategy to manage the state and return it in this method.
     * @return the implementation of state manager that suplies the strategy defined
     */
    public StateManager getStateManager();
    
    /*
     * This method is invoked by ServiceReplica to indicate that a consensus instance
     * finished without delivering anything to the application (e.g., an instance
     * only decided a single reconfiguration operation. or an instance where the client
     * operation was not delivered because its view was outdated). To allow the underlying state
     * transfer protocol to execute correctly, it needs to be notified of this special case
     * In the current protocols included, it suffices to register a NOOP operation in the
     * logs used within the state transfer, but never deliver it to the application
     * @param lastEid the consensus instance where the aforementioned condition occurred
     */
    public void noOp(int lastEid); 
	
}
