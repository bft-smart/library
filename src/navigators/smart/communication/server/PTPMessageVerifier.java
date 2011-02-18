/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface PTPMessageVerifier extends MessageVerifier<byte[]>{

    /**
     * Generates a hash for the given messagedata
     * @param messageData
     * @return The hash of the data
     */
    public byte[] generateHash(byte[] messageData);

}
