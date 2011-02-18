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
public interface GlobalMessageVerifier<A> extends MessageVerifier<A>{

    /**
     * Generates a hash for the given messagedata
     * @param message The message to generate the hash for
     */
    public void generateHash(SystemMessage message);
}
