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
package bftsmart.reconfiguration.util;

import java.util.HashMap;
import java.util.Map;

import bftsmart.tom.util.KeyLoader;

/**
 * Fluent builder for a {@link TOMConfiguration} that is configured purely in memory,
 * without any {@code system.config} / {@code hosts.config} file on disk.
 *
 * <p>Any {@code system.*} property can be set with {@link #property(String, Object)};
 * a few common ones have convenience methods. Hosts are registered with
 * {@link #host(int, String, int, int)}. Missing properties fall back to the same
 * defaults used when parsing {@code system.config}.</p>
 *
 * <pre>{@code
 * TOMConfiguration conf = new TOMConfigurationBuilder()
 *         .servers(4).f(1).initialView(0, 1, 2, 3)
 *         .listeners(4)                              // optional, if the listener role is available
 *         .defaultKeys(true).useSignatures(false)
 *         .host(0, "127.0.0.1", 11000, 11001)
 *         .host(1, "127.0.0.1", 11010, 11011)
 *         .host(2, "127.0.0.1", 11020, 11021)
 *         .host(3, "127.0.0.1", 11030, 11031)
 *         .build(0);                                 // configuration for replica 0
 * }</pre>
 */
public class TOMConfigurationBuilder {

    private final Map<String, String> props = new HashMap<>();
    private final HostsConfig hosts = new HostsConfig();

    /** Sets an arbitrary {@code system.*} property. */
    public TOMConfigurationBuilder property(String key, Object value) {
        props.put(key, String.valueOf(value));
        return this;
    }

    /** Registers a host: client port then server-to-server port (as in hosts.config). */
    public TOMConfigurationBuilder host(int id, String ip, int clientPort, int serverToServerPort) {
        hosts.add(id, ip, clientPort, serverToServerPort);
        return this;
    }

    public TOMConfigurationBuilder servers(int n) {
        return property("system.servers.num", n);
    }

    public TOMConfigurationBuilder f(int f) {
        return property("system.servers.f", f);
    }

    public TOMConfigurationBuilder initialView(int... ids) {
        return property("system.initial.view", join(ids));
    }

    /** Declares non-voting members (listeners), if that role is supported by the build. */
    public TOMConfigurationBuilder listeners(int... ids) {
        return property("system.servers.listeners", join(ids));
    }

    public TOMConfigurationBuilder defaultKeys(boolean enabled) {
        return property("system.communication.defaultkeys", enabled);
    }

    public TOMConfigurationBuilder useSignatures(boolean enabled) {
        return property("system.communication.useSignatures", enabled ? 1 : 0);
    }

    public TOMConfigurationBuilder bft(boolean bft) {
        return property("system.bft", bft);
    }

    /** Sets the TLS keystore file (looked up under config/keysSSL_TLS/). */
    public TOMConfigurationBuilder sslKeyStore(String fileName) {
        return property("system.ssltls.key_store_file", fileName);
    }

    /** Sets the enabled TLS cipher suites for the replica-to-replica transport. */
    public TOMConfigurationBuilder enabledCiphers(String... ciphers) {
        StringBuilder sb = new StringBuilder();
        for (String c : ciphers) sb.append(c).append(",");
        return property("system.ssltls.enabled_ciphers", sb.toString());
    }

    /**
     * Sets the durable state storage directory for this group. In multi-group deployments
     * pass the result of {@link bftsmart.multigroup.GroupStorageLayout#dirFor(int)} here so
     * each group writes its checkpoint/log files to an isolated sub-directory.
     */
    public TOMConfigurationBuilder storageDir(String dir) {
        props.put("__storageDir__", dir);
        return this;
    }

    /**
     * Sets the durable state-transfer port base. Actual port = base + replicaId.
     * Default is 4444. Set a distinct base per group in multi-group JVM deployments
     * (e.g. group 0 → 4444, group 1 → 5444, group 2 → 6444).
     */
    public TOMConfigurationBuilder stateTransferPortBase(int portBase) {
        props.put("__stateTransferPortBase__", String.valueOf(portBase));
        return this;
    }

    /** Builds the configuration for the given process, using the default key loader. */
    public TOMConfiguration build(int processId) {
        return build(processId, null);
    }

    /** Builds the configuration for the given process, using the supplied key loader. */
    public TOMConfiguration build(int processId, KeyLoader loader) {
        TOMConfiguration conf = new TOMConfiguration(processId, loader, new HashMap<>(props), hosts);
        String sd = props.get("__storageDir__");
        if (sd != null) conf.setStorageDir(sd);
        String ptb = props.get("__stateTransferPortBase__");
        if (ptb != null) conf.setStateTransferPortBase(Integer.parseInt(ptb));
        return conf;
    }

    private static String join(int[] ids) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(ids[i]);
        }
        return sb.toString();
    }
}
