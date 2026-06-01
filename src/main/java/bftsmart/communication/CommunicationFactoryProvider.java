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
package bftsmart.communication;

/**
 * Programmatic registry for the {@link CommunicationFactory} used by the entry points
 * ({@code ServiceReplica}, {@code ServiceProxy}, reconfiguration tooling, ...) when no
 * factory is supplied explicitly through a constructor.
 *
 * <p>There is intentionally no service discovery (no {@code ServiceLoader}, no reflection
 * by class name): the wiring is fully explicit. An application selects its transport by
 * calling {@link #setDefaultFactory(CommunicationFactory)} once during start-up, e.g.</p>
 *
 * <pre>{@code
 * CommunicationFactoryProvider.setDefaultFactory(new bftsmart.communication.tls.TLSNettyCommunicationFactory());
 * }</pre>
 *
 * <p>Alternatively the factory can be passed directly to the constructors that accept a
 * {@link CommunicationFactory}, in which case this registry is not consulted.</p>
 */
public final class CommunicationFactoryProvider {

    private static volatile CommunicationFactory defaultFactory;

    private CommunicationFactoryProvider() {
    }

    /**
     * Registers the factory to be used by entry points that are constructed without an
     * explicit {@link CommunicationFactory}.
     *
     * @param factory the factory to use (must not be {@code null})
     */
    public static void setDefaultFactory(CommunicationFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("CommunicationFactory must not be null");
        }
        defaultFactory = factory;
    }

    /**
     * @return whether a default {@link CommunicationFactory} has been registered
     */
    public static boolean hasDefaultFactory() {
        return defaultFactory != null;
    }

    /**
     * Returns the registered default factory.
     *
     * @return the default {@link CommunicationFactory}
     * @throws IllegalStateException if no factory has been registered
     */
    public static CommunicationFactory getDefaultFactory() {
        CommunicationFactory f = defaultFactory;
        if (f == null) {
            throw new IllegalStateException(
                    "No CommunicationFactory has been configured. Either register one at start-up via "
                            + "CommunicationFactoryProvider.setDefaultFactory(...) "
                            + "(e.g. new bftsmart.communication.tls.TLSNettyCommunicationFactory() from the "
                            + "bftsmart-tls module), or use a constructor that accepts a CommunicationFactory.");
        }
        return f;
    }
}
