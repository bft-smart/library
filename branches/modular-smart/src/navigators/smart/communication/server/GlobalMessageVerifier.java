/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

import navigators.smart.tom.core.messages.SystemMessage;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface GlobalMessageVerifier<A>{

    /**
     * Generates a hash for the given messagedata
     * @param message The message to generate the hash for
     */
    public void generateHash(SystemMessage message);
    
    /**
     * Returns the decoded verification data if valid, null otherwise. With that
     * Functionality it is possible to use e.g. the USIG mechanism in ebawa
     * where monotonically rising numbers are generated.
     * @param data The data to check
     * @return The deserialised verification data if the data was valid
     */
    public A verifyHash(byte[] data);
    
    /**
     * Initialises this verifier.
     */
	public void authenticateAndEstablishAuthKey();
}
