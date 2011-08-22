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

package navigators.smart.communication.client;

import navigators.smart.tom.core.messages.TOMMessage;

/**
 * Methods that should be implemented by the client side of the client-server communication system
 *
 * @author Paulo
 */
public interface CommunicationSystemClientSide {
   public void send(boolean sign, int[] targets, TOMMessage sm);
   public void setReplyReceiver(ReplyReceiver trr);
   public void sign(TOMMessage sm);
   public void close();

   //******* EDUARDO BEGIN **************//
   public void updateConnections();
   //******* EDUARDO END **************//

}
