package bftsmart.tom.server;

import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.StateManager;
import bftsmart.tom.ReplicaContext;

/**
 * 
 * @author mhsantos
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
	
}
