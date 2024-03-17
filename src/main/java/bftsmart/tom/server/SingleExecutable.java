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
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.util.ServiceContent;
import bftsmart.tom.util.TOMUtil;

/**
 * Executables that implement this interface will receive client requests individually.
 *
 */
public interface SingleExecutable extends Executable {

	/**
	 * Method called to execute a request totally ordered.
	 * The message context contains a lot of information about the request, such
	 * as timestamp, nonce and sender. The code for this method MUST use the value
	 * of timestamp instead of relying on its own local clock, and nonce instead
	 * of trying to generate its own random values.
	 * This is important because these values are the same for all replicas, and
	 * therefore, ensure the determinism required in a replicated state machine.
	 *
	 * @param command the command issue by the client
	 * @param msgCtx information related with the command
	 *
	 * @return the reply for the request issued by the client
	 */
	ServiceContent executeOrdered(byte[] command, byte[] replicaSpecificContent, MessageContext msgCtx);

	default TOMMessage executeOrdered(int processID, int viewID, byte[] commonContent,
									  byte[] replicaSpecificContent, MessageContext msgCtx) {

		ServiceContent response = executeOrdered(commonContent, replicaSpecificContent, msgCtx);
		if (response == null) {
			return null;
		}
		byte[] result = response.getCommonContent();

		return getTOMMessage(processID, viewID, commonContent, msgCtx, result, response.getReplicaSpecificContent());

	}

}
