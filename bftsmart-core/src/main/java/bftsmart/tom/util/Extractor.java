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
package bftsmart.tom.util;

import bftsmart.tom.core.messages.TOMMessage;

/**
 * Provides support for building custom response extractors to be used in the
 * ServiceProxy.
 *
 */
public interface Extractor {
    
    /**
     * Extracts a reply given a set of replies from a set of replicas.
     * 
     * @param replies Set of replies from a set of replicas.
     * @param sameContent Whether or not the replies are supposed to have the same content
     * @param lastReceived Last reply received from the replicas. This is an index in relation to the `replies` parameter.
     * @return 
     */
    TOMMessage extractResponse(TOMMessage[] replies, int sameContent, int lastReceived);
}
