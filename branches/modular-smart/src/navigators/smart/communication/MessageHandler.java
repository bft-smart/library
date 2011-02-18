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

package navigators.smart.communication;

import java.io.IOException;
import java.nio.ByteBuffer;

import navigators.smart.tom.core.messages.SystemMessage;

/**
 *
 * @param <A> Resulttype of the verification of this message
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public interface MessageHandler<A, M extends SystemMessage> {

    /**
     * Processes the SystemMessage sm if it is adressed to this handler
     * @param sm The message to process
     */
    public void processData(SystemMessage sm);

    /**
     * Deserialises the SystemMessage of the given type
     * @param type The type to indicate the Object to create
     * @param in The ByteBuffer containing the serialised object
     * @param result The result of the verification of the message
     * @return The created Object if the type is valid for this handler, null otherwhise
     *
     * @throws ClassNotFoundException If there are Objects seralised within and the class of these objects is not found
     * @throws IOException If something else goes wrong upon deserialisation
     */
    public M deserialise(SystemMessage.Type type, ByteBuffer in, A result) throws ClassNotFoundException,IOException;

}
