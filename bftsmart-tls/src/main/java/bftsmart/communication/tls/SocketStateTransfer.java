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
package bftsmart.communication.tls;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.communication.server.StateTransferSender;
import bftsmart.statemanagement.ApplicationState;

/**
 * Socket-based implementation of the bulk state-transfer channel used by the durable
 * state manager. The state provider opens a {@link ServerSocket}, waits for the
 * recovering replica to connect, and streams a single serialized {@link ApplicationState};
 * the receiver connects and reads it back.
 *
 * <p>Historically this was the {@code StateSenderServer}/{@code StateSender} pair living
 * in the core; it has been moved behind the {@link bftsmart.communication.CommunicationFactory}
 * SPI so that an alternative transport can provide its own bulk-transfer channel.</p>
 *
 * <p>Note: like the original code, this channel is plaintext TCP (no TLS).</p>
 */
final class SocketStateTransfer {

    private static final Logger logger = LoggerFactory.getLogger(SocketStateTransfer.class);

    private SocketStateTransfer() {
    }

    /**
     * Starts a provider endpoint that serves a single state to the first peer that connects.
     */
    static StateTransferSender serve(InetSocketAddress bindAddress, Supplier<ApplicationState> stateSupplier) {
        Sender sender = new Sender(bindAddress, stateSupplier);
        sender.start();
        return sender;
    }

    /**
     * Connects to a provider endpoint and reads a single {@link ApplicationState}.
     */
    static ApplicationState fetch(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket(address.getHostName(), address.getPort());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            return (ApplicationState) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize application state object", e);
        }
    }

    private static final class Sender extends Thread implements StateTransferSender {

        private final InetSocketAddress bindAddress;
        private final Supplier<ApplicationState> stateSupplier;
        private volatile ServerSocket server;

        Sender(InetSocketAddress bindAddress, Supplier<ApplicationState> stateSupplier) {
            super("Durable-StateTransfer-Sender");
            this.bindAddress = bindAddress;
            this.stateSupplier = stateSupplier;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                server = new ServerSocket(bindAddress.getPort());
            } catch (IOException e) {
                logger.error("Could not open state-transfer server socket on port " + bindAddress.getPort(), e);
                return;
            }
            try (Socket socket = server.accept()) {
                ApplicationState state = stateSupplier.get();
                logger.debug("Sending state over the bulk state-transfer channel");
                ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(state);
                oos.flush();
                logger.debug("Sent state over the bulk state-transfer channel");
            } catch (IOException e) {
                logger.error("Problem serving state over the bulk state-transfer channel", e);
            } finally {
                shutdown();
            }
        }

        @Override
        public void shutdown() {
            ServerSocket s = server;
            if (s != null && !s.isClosed()) {
                try {
                    s.close();
                } catch (IOException e) {
                    logger.debug("Failed to close state-transfer server socket", e);
                }
            }
        }
    }
}
