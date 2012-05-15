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

package bftsmart.tom;

import bftsmart.tom.core.messages.TOMMessage;

/**
 * Interface meant for objects that receive requests from clients
 */
public interface TOMReceiver {

    /**
     * This is the method invoked by the DeliveryThread to deliver a message to the receiver.
     * It is used to receive readonly messages. These types of messages are delivered as
     * soon as they are received by TomLayer 
     *
     * @param msg The request delivered by the TOM layer
     */
    public void receiveReadonlyMessage(TOMMessage msg, MessageContext msgCtx);

	/**
     * This is the method invoked by the DeliveryThread to deliver a batch of messages to
     * the receiver.
     * If the receiver implements the BatchExcetutable interface, the messages are delivered
     * as a bath. Otherwise, messages are delivered one by one. 
     *
	 * @param consId The id of the consensus in which the messages were ordered.
	 * @param regency
	 * @param requests The batch with TOMMessage objects.
	 */
    public void receiveMessages(int consId, int regency, boolean fromConsensus, TOMMessage[] requests, byte[] decision);


    /**
     * This method is used by the TOM Layer to retrieve the state of the application. This must be
     * implemented by the programmer, and it should retrieve an array of bytes that contains the application
     * state.
     * @return An rray of bytes that can be diserialized into the application state
     */
    //public byte[] getState();

    /**
     * This method is invoked by the TOM Layer in order to ser a state upon the aplication. This is done when
     * a replica is delayed compared to the rest of the group, and when it recovers after a failure.
     */
    //public void setState(byte[] state);

}

