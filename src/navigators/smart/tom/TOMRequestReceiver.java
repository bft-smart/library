/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom;

import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Interface meant for objects that receive requests from clients
 */
public interface TOMRequestReceiver {

    /**
     * This is the method invoked by the DeliveryThread to deliver a totally
     * ordered request, and where the code to handle the request is to be written
     *
     * @param msg the delivered request
     * @param msgCtx the context of the message being delivered
     */
    public void receiveOrderedMessage(TOMMessage msg, MessageContext msgCtx);

    /**
     * This is the method invoked by the TOMLayer to deliver a read only request
     * that was not totally ordered
     *
     * @param msg The request delivered by the TOM layer
     */
    public void receiveMessage(TOMMessage msg, MessageContext msgCtx);

    /**
     * This method is used by the TOM Layer to retrieve the state of the application. This must be
     * implemented by the programmer, and it should retrieve an array of bytes that contains the application
     * state.
     * @return An rray of bytes that can be diserialized into the application state
     */
    public byte[] getState();

    /**
     * This method is invoked by the TOM Layer in order to ser a state upon the aplication. This is done when
     * a replica is delayed compared to the rest of the group, and when it recovers after a failure.
     */
    public void setState(byte[] state);

    /**
     * This method waits for the batch of messages to finish being processed
     */
    public void waitForProcessingRequests();
}

