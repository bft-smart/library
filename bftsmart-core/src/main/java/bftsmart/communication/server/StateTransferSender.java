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
package bftsmart.communication.server;

/**
 * Abstraction of the bulk state-transfer channel used by the durable state manager.
 *
 * <p>This is a dedicated, point-to-point channel — separate from the consensus message
 * bus ({@link ServerCommunicationLayer}) — used to stream a (potentially large)
 * {@code ApplicationState} from a state provider to a recovering replica. A
 * {@code StateTransferSender} represents the provider endpoint: it listens for the
 * recovering replica and serves the state once. Instances are created through a
 * {@link bftsmart.communication.CommunicationFactory}; the matching receive side is
 * {@link bftsmart.communication.CommunicationFactory#fetchState}.</p>
 */
public interface StateTransferSender {

    /**
     * Releases the endpoint, stopping it from accepting (further) connections.
     */
    void shutdown();
}
