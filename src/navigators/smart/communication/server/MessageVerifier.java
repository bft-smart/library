/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

/**
 *
 * This Interface represents a messageverifier, that creates hashes for messages
 * to send and verfies messages received. To represent the capabilities of a 
 * Unique Sequential Identifier Generator the result of a verification is not
 * a boolean but an object containing the appended verification information.
 *
 * @param <A> This is the Type of the objects returned when authenticating a message
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface MessageVerifier<A> {

    /**
     * Initialises this verifier.
     */
    public void authenticateAndEstablishAuthKey();

    /**
     * Generates a hash for the given messagedata
     * @param messageData
     * @return The hash of the data
     */
    public byte[] generateHash(byte[] messageData);

    /**
     * Returns the size of the generated hashes by this verifier
     * @return
     */
    public int getHashSize();

    /**
     * Returns the decoded verificationdata if valid, null otherwhise. With that
     * funcitonality it is possible to use e.g. the USIG mechanism in ebawa
     * where monotonically rising numbers are generated.
     * @param data The data to check
     * @param receivedHash The hash
     * @return The deserialised verificationdata if the data was valid
     */
    public A verifyHash(byte[] data, byte[] receivedHash);

}
