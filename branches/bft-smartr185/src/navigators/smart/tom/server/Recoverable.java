package navigators.smart.tom.server;

/**
 * Interface to implement an object that serializes/deserializes the application
 * state, and passes it to the state transfer protocol
 * @author mhsantos
 *
 */
public interface Recoverable {
	
    /**
     * This method is used by the TOM Layer to retrieve the state of the application. This must be
     * implemented by the programmer, and it should retrieve an array of bytes that contains the application
     * state.
     * @return An array of bytes that can be diserialized into the application state
     */
    public byte[] getState();

    /**
     * This method is invoked by the TOM Layer in order to ser a state upon the aplication. This is done when
     * a replica is delayed compared to the rest of the group, and when it recovers after a failure.
     * 
     * @param state a byte array that is the serialization of the application's state
     */
    public void setState(byte[] state);
	
}
