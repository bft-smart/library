/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package bftsmart.communication.client;

import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;

import java.util.Map;

/**
 * Methods that should be implemented by the client side of the client-server communication system
 *
 * @author Paulo
 */
public interface CommunicationSystemClientSide {
   public void send(boolean sign, int[] targets, TOMMessage sm);

   //******* ROBIN BEGIN **************//
   public void send(boolean sign, int[] targets, byte[] commonData, Map<Integer, byte[]> privateData, byte metadata,
                    int sender, int session, int reqId, int operationId, int view, TOMMessageType type);
   //******* ROBIN END **************//

   public void setReplyReceiver(ReplyReceiver trr);
   public void sign(TOMMessage sm);
   public void close();

   //******* EDUARDO BEGIN **************//
   public void updateConnections();
   //******* EDUARDO END **************//

}
