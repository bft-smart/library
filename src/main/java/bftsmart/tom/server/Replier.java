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
package bftsmart.tom.server;

import bftsmart.tom.MessageContext;
import bftsmart.tom.ReplicaContext;
import bftsmart.tom.core.messages.TOMMessage;

/**
 * Provides support for building custom reply management
 * to be used in the ServiceReplica.
 * 
 */
public interface Replier {
    
    /**
     * Sets the replica context
     * @param replicaContext  The replica context
     */
    public void setReplicaContext(ReplicaContext replicaContext);

    /**
     * Given an executed request, send it to a target
     * 
     * @param request The executed request
     * @param msgCtx The message context associated to the request
     */
    public void manageReply(TOMMessage request, MessageContext msgCtx);

}

