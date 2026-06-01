# Byzantine Fault-Tolerant (BFT) State Machine Replication (SMaRt) v2.0

This is a Byzantine fault-tolerant state machine replication project named BFT-SMaRt, a Java open source library maintained by the LASIGE Computer Science and Engineering Research Centre at the University of Lisbon.

This package contains the source code, dependencies (lib/), documentation (docs/), running scripts (runscripts/), and configuration files (config/) for version 2.0 of the project.

---

## What's different from upstream BFT-SMaRt (this fork)

This fork keeps the BFT-SMaRt protocol behaviour and public programming model intact,
but introduces a set of **surgical, additive** changes. With the default configuration
and the legacy constructors, behaviour is unchanged; the new capabilities are opt-in.

1. **Gradle multi-module build.** The single project was split into two modules so the
   networking transport can be swapped without touching the protocol code:
   - `bftsmart-core` — consensus / TOM / reconfiguration / state transfer + the
     networking SPI. No dependency on any networking library.
   - `bftsmart-tls` — the default transport (TLS server sockets + Netty) and the
     runnable demos / tests / benchmarks.

   `./gradlew installDist` now produces `bftsmart-tls/build/install/bftsmart-tls`
   (see *Project structure* and *Compiling* below). A future transport (e.g. Apache
   Pekko) is just a new module providing its own `CommunicationFactory`.

2. **Pluggable networking layer (SPI).** All networking goes through
   `bftsmart.communication.CommunicationFactory`: replica-to-replica transport
   (`ServerCommunicationLayer`), client-to-server transport (`CommunicationSystem*`),
   one-shot replica connections (`ReplicaConnection`) and the bulk state-transfer
   channel (`StateTransferSender` / `fetchState`). Wiring is **programmatic** (no
   `ServiceLoader`). Netty was removed from the core (it had only a dead reference).

3. **Programmatic, file-less configuration.** Besides `system.config` / `hosts.config`,
   the system can now be configured entirely in memory via `TOMConfigurationBuilder`
   and the new `TOMConfiguration(...)`-accepting constructors of `ServiceReplica` /
   `ServiceProxy`. See *Programmatic configuration* and
   `bftsmart.demo.programmatic.ProgrammaticConfigDemo`.

4. **View storage without reflection.** The `view.storage.handler` reflective plug-in
   was replaced by programmatic injection (`ViewStorageProvider.setFactory(...)`),
   defaulting to the file-based `DefaultViewStorage`.

5. **Non-voting members ("listeners" / learners).** A replica can join as a *listener*:
   it replicates the service state (from forwarded, proof-verified decisions) but does
   **not** vote in consensus and is never counted in any quorum. A node can be
   **promoted to voter or demoted to listener at runtime** through a view change
   (`VMServices.addListener / promoteToVoter / demoteToListener`). See *Listeners*.

6. **Custom binary serialization.** Java object serialization
   (`ObjectOutputStream`/`ObjectInputStream`) was replaced by a compact binary codec
   (`bftsmart.tom.util.io.*`) for application state, consensus / leader-change /
   reconfiguration messages, the decision proofs, and the **replica-to-replica
   transport envelope** (`SystemMessageCodec`). This removes the reflective-serialization
   bottleneck on the hot path (large deserialization speed-ups; see
   `docs/state-serialization-benchmark.md`).

7. **Dead-code cleanup.** Removed an unwired NIO `SocketChannel` state-transfer path.

> Status note: items 1–4 and 7 are merged into `master`; items 5 (listeners) and 6
> (binary serialization, including the transport envelope) are integrated together on
> the `claude/integration-binary-serialization` branch.

---

## Quick start

To run any demonstration you first need to configure BFT-SMaRt to define the protocol behavior and the location of each replica.

The servers must be specified in the configuration file (see `config/hosts.config`):

```
#server id, address and port (the ids from 0 to n-1 are the service replicas) 
0 127.0.0.1 11000 11001
1 127.0.0.1 11010 11011
2 127.0.0.1 11020 11021
3 127.0.0.1 11030 11031
```

**Important tip #1:** Always provide IP addresses instead of hostnames. If a machine running a replica is not correctly configured, BFT-SMaRt may fail to bind to the appropriate IP address and use the loopback address instead (127.0.0.1). This phenomenon may prevent clients and/or replicas from successfully establishing a connection among them.

**Important tip #2:** Clients requests should not be issued before all replicas have been properly initialized. Replicas are ready to process client requests when each one outputs `Ready to process operations` in the console.

The system configurations also have to be specified (see`config/system.config`). Most of the parameters are self-explanatory.

**Important tip #3:** When using the library in real systems, always make sure to set `system.communication.defaultkeys` to `false` and `system.communication.useSignatures` to `1`. Also make sure that only the `config/keys` directory has the private key for the respective replica/client.

## Compiling

Type `./gradlew installDist` in the main directory. The required jar files and default configuration files will be available in the `bftsmart-tls/build/install/bftsmart-tls` directory.

**WARNING:** You might need to give execution permission to the `gradlew` script.

Copy content of `bftsmart-tls/build/install/bftsmart-tls` into multiple folders for local testing or machines for distributed testing.

## Project structure (networking abstraction)

The project is organized as a Gradle multi-module build so that the networking
transport can be swapped without touching the consensus/replication logic:

* **`bftsmart-core`** — the protocol layers (consensus, total order multicast,
  reconfiguration, state transfer) and the networking SPI. The key interface is
  `bftsmart.communication.CommunicationFactory`, which abstracts every networking
  component: the replica-to-replica transport (`ServerCommunicationLayer`), the
  client-to-server transport (`CommunicationSystemServerSide` /
  `CommunicationSystemClientSide`), one-shot replica connections
  (`ReplicaConnection`) and the bulk state-transfer channel used by the durable
  state manager (`StateTransferSender` / `CommunicationFactory.fetchState`). The
  core has no dependency on any networking library.
* **`bftsmart-tls`** — the default transport implementation, built on TLS server
  sockets (replica-to-replica) and Netty (client-to-server), exposed through
  `bftsmart.communication.tls.TLSNettyCommunicationFactory`. This module also hosts
  the runnable demos, integration tests and benchmarks.

The transport is selected **programmatically** (there is no `ServiceLoader`-based
discovery). An application either passes a `CommunicationFactory` to the relevant
constructor (`ServiceReplica`, `ServiceProxy`, `AsynchServiceProxy`, ...) or registers
a default once at start-up:

```java
CommunicationFactoryProvider.setDefaultFactory(new TLSNettyCommunicationFactory());
// shorthand:
TLSNettyCommunicationFactory.installAsDefault();
```

To use a different transport (e.g. Apache Pekko), add a new module that depends on
`bftsmart-core` and provides its own `CommunicationFactory` implementation, then wire
it in the same way.

## Programmatic configuration

In addition to `config/system.config` + `config/hosts.config`, the system parameters
and host addresses can be supplied entirely from code, with no configuration files on
disk, using `TOMConfigurationBuilder`:

```java
TOMConfiguration conf = new TOMConfigurationBuilder()
        .servers(4).f(1).initialView(0, 1, 2, 3)
        .defaultKeys(true).useSignatures(false)
        .enabledCiphers("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
        .host(0, "127.0.0.1", 11000, 11001)
        .host(1, "127.0.0.1", 11010, 11011)
        .host(2, "127.0.0.1", 11020, 11021)
        .host(3, "127.0.0.1", 11030, 11031)
        .build(myId);

new ServiceReplica(conf, service, service, null, null, new TLSNettyCommunicationFactory());
// client: new ServiceProxy(conf, null, null, new TLSNettyCommunicationFactory());
```

Runnable example: `bftsmart.demo.programmatic.ProgrammaticConfigDemo`. (For the default
TLS transport the keystore under `config/keysSSL_TLS/` is still read from disk — that is
crypto material, not BFT-SMaRt configuration.)

## Listeners (non-voting members)

A node can participate as a **listener** (learner): it replicates the service state but
does not vote in consensus and is never counted in any quorum. Listeners receive each
decided value together with its quorum certificate (a set of signed `ACCEPT`s) and apply
it only after verifying that proof, so they cannot be fed a forged state by a single
replica.

Declare listeners statically in `config/system.config`:

```
system.servers.num = 4            # voters
system.initial.view = 0,1,2,3
system.servers.listeners = 4      # non-voting member(s), comma-separated ids
```

…or change a node's role at runtime, through a (consensus-ordered) view change:

```java
VMServices vm = new VMServices();
vm.addListener(5, "127.0.0.1", 11050, 11051); // join as a listener
vm.promoteToVoter(5);                          // listener -> voter
vm.demoteToListener(0);                        // voter   -> listener
```

Promotion is cheap because a listener is already state-synchronised. Demotion/removal of
a voter reduces `n`, so the remaining voters must still satisfy `n >= 3f+1` (BFT).
Reproducible tests: `bftsmart-tls/scripts/listener-replication-test.sh` and
`listener-hardening-test.sh` (late-join state transfer + leader change with a listener).

## Programmatic API — basic functions

A quick reference of the core operations, all driven from code. Replace the demo
`Counter` with your own `Recoverable` / `Executable`.

**1. Build a group's configuration (no config files):**
```java
TOMConfiguration conf = new TOMConfigurationBuilder()
        .servers(4).f(1).initialView(0, 1, 2, 3)
        .defaultKeys(true).useSignatures(false)
        .enabledCiphers("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256")
        .host(0, "127.0.0.1", 11000, 11001)
        .host(1, "127.0.0.1", 11010, 11011)
        .host(2, "127.0.0.1", 11020, 11021)
        .host(3, "127.0.0.1", 11030, 11031)
        // start node 4 directly as a LISTENER: .listeners(4).host(4,"127.0.0.1",11040,11041)
        .build(myId);
```

**2. Start a replica and a client:**
```java
Counter service = new Counter();                       // your DefaultSingleRecoverable
new ServiceReplica(conf, service, service, null, null, // executor == recoverer
                   new TLSNettyCommunicationFactory());
// file-less / isolated: add a per-instance view storage:
//   new ServiceReplica(conf, service, service, null, null,
//                      new TLSNettyCommunicationFactory(), new InMemoryViewStorage());

ServiceProxy proxy = new ServiceProxy(conf, null, null, new TLSNettyCommunicationFactory());
byte[] reply = proxy.invokeOrdered(request);   // ordered (consensus); invokeUnordered() for reads
proxy.close();
```

**3. Add voters / listeners at runtime (consensus-ordered view change):**
```java
VMServices vm = new VMServices();                  // issued by the configured TTP
vm.addVoter(4, "127.0.0.1", 11040, 11041);         // add a VOTER (participates in consensus)
vm.addListener(5, "127.0.0.1", 11050, 11051);      // add a LISTENER (replicates, no vote)
vm.promoteToVoter(5);                              // LISTENER -> VOTER (already state-synced)
vm.demoteToListener(4);                            // VOTER -> LISTENER (keep n >= 3f+1)
vm.removeServer(4);                                // remove a member
```

Listeners can also be declared statically in `config/system.config`:
```
system.servers.listeners = 4      # comma-separated ids
```

**4. Host several consensus groups in one process (multi-group / multi-raft):**
```java
MultiGroupReplica mgr = new MultiGroupReplica("config");        // dir for per-group view files
mgr.addGroup(0, group0Conf, svcA, svcA, new TLSNettyCommunicationFactory());          // durable view
mgr.addInMemoryGroup(1, group1Conf, svcB, svcB, new TLSNettyCommunicationFactory());  // ephemeral view
ServiceReplica g0 = mgr.group(0);
```
Each group is fully isolated (own view, consensus sequence, leader, state machine and
view storage). Use distinct ports per group. A node may be a voter in one group and a
listener in another. (An experimental `addSharedGroup(...)` multiplexes groups over a
single replica-to-replica port; see the multi-group branch.)

**The state machine** to implement (`DefaultSingleRecoverable`):
```java
byte[] appExecuteOrdered(byte[] command, MessageContext ctx);   // mutate + reply
byte[] appExecuteUnordered(byte[] command, MessageContext ctx); // read-only reply
byte[] getSnapshot();                                           // full state -> bytes
void   installSnapshot(byte[] state);                           // bytes -> full state
```

## Running the counter demonstration
You can run the counter demonstration by executing the following commands, from within the folders containing compiled code across four different consoles (4 replicas, to tolerate 1 fault):

```
./smartrun.sh bftsmart.demo.counter.CounterServer 0
./smartrun.sh bftsmart.demo.counter.CounterServer 1
./smartrun.sh bftsmart.demo.counter.CounterServer 2
./smartrun.sh bftsmart.demo.counter.CounterServer 3
```

**Important tip #4:** If you are getting timeout messages, it is possible that the application you are running takes too long to process the requests or the network delay is too high and PROPOSE messages from the leader does not arrive in time, so replicas may start the leader change protocol. To prevent that, try to increase the `system.totalordermulticast.timeout` parameter in `config/system.config`.

**Important tip #5:** Never forget to delete the `config/currentView` file after you modify `config/hosts.config` or `config/system.config`. If `config/currentView` exists, BFT-SMaRt always fetches the group configuration from this file first. Otherwise, BFT-SMaRt fetches information from the other files and creates `config/currentView` from scratch. Note that `config/currentView` only stores information related to the group of replicas. You do not need to delete this file if, for instance, you want to change the value of the request timeout.

Once all replicas are ready, the client can be launched as follows:

```
./smartrun.sh bftsmart.demo.counter.CounterClient 1001 <increment> [<number of operations>]
```

If `<increment>` equals 0 the request will be read-only. Default `<number of operations>` equals 1000.

**Important tip #6:** Always make sure that each client uses a unique ID. Otherwise, clients may not be able to complete their operations.

## Read-only optimization

BFT-SMaRt implements a read-only optimization that allows replicas to process read-only requests without executing consensus protocol.
Recent work (see section Additional information and publications) has shown that this optimization could violate the liveness property of the system.

The recent BFT-SMaRt version implements the proposed solution, which guarantees that the system will not violate the live property when using the read-only optimization.
However, due to the high memory consumption of the current implementation, this optimization is turned off by default but can be enabled by setting the `system.optimizations.readonly_requests` parameter to `true` in the `config/system.config` file.


## State transfer protocol(s)

BFT-SMaRt offers two state transfer protocols. The first is a basic protocol that can be used by extending the classes `bftsmart.tom.server.defaultservices.DefaultRecoverable` and `bftsmart.tom.server.defaultservices.DefaultSingleRecoverable`. These classes log requests into memory and periodically take snapshots of the application state.

The second, more advanced protocol can be used by extending the class 
`bftsmart.tom.server.defaultservices.durability.DurabilityCoordinator`. This protocol stores its logs to disk. To mitigate the latency of writing to disk, such tasks are done in batches and in parallel with the requests' execution. Additionally, the snapshots are taken at different points of the execution in different replicas.

**Important tip #7:** We recommend developers to use `bftsmart.tom.server.defaultservices.DefaultRecoverable`, since it is the most stable of the three classes.

**Important tip #8:** Regardless of the chosen protocol, developers must avoid using Java API objects like `HashSet` or `HashMap`, and use `TreeSet` or `TreeMap` instead. This is because serialization of Hash* objects is not deterministic, i.e, it generates different byte arrays for equal objects. This will lead to problems after more than `f` replicas used the state transfer protocol to recover from failures.

## Group reconfiguration

The library also implements a reconfiguration protocol that can be used to add/remove replicas from the initial group.

You can add a replica to the group on-the-fly by executing the following command:
```
./smartrun.sh bftsmart.reconfiguration.util.DefaultVMServices <smart id> <ip address> <port client-to-replica> <port replica-to-replica>
```

You can remove a replica from the group on-the-fly by executing the following command:
```
./smartrun.sh bftsmart.reconfiguration.util.DefaultVMServices <smart id>
```

**Important tip #9:** Everytime you use the reconfiguration protocol, you must make sure that all replicas and the host where you invoke the above commands have the latest `config/currentView` file. The current implementation of BFT-SMaRt does not provide any mechanism to distribute this file, so you will need to distribute it on your own (e.g., using the `scp` command). You also need to make sure that any client that starts executing can read from the latest `config/currentView` file.

## BFT-SMaRt under crash faults

You can run BFT-SMaRt in crash-faults only mode by setting the `system.bft` parameter in the configuration file to `false`. This mode requires fewer replicas to execute, but will not withstand full Byzantine behavior from compromised replicas.

## Generating public/private key pairs

If you need to generate public/private keys for more replicas or clients, you can use the following command.

To generate RSA key pairs, execute the following command:
```
./smartrun.sh bftsmart.tom.util.RSAKeyPairGenerator <id> <key length> [config dir]
```

To generate ECDSA key pairs, execute the following command:
```
./smartrun.sh bftsmart.tom.util.ECDSAKeyPairGenerator <id> <domain parameter> [config dir]
```
Default config dir are `config/keysRSA` and `config/keysECDSA`, respectively.
The commands above create key pairs both for clients and replicas. Alternatively, you can set the `system.communication.defaultkeys` to `true` in the `config/system.config` file to forces all processes to use the same public/private keys pair and secret key. This is useful when deploying experiments and benchmarks, because it enables the programmer to avoid generating keys for all principals involved in the system. However, this must not be used in a real deployments.

## Additional information and publications

If you are interested in learning more about BFT-SMaRt, you can read:

- The paper about its state machine protocol published in [EDCC'12](http://www.di.fc.ul.pt/~bessani/publications/edcc12-modsmart.pdf);
- The paper about its advanced state transfer protocol published in [Usenix'13](http://www.di.fc.ul.pt/~bessani/publications/usenix13-dsmr.pdf);
- The tool description published in [DSN'14](http://www.di.fc.ul.pt/~bessani/publications/dsn14-bftsmart.pdf);
- The paper about read-only optimization published in [SRDS'21](https://arxiv.org/pdf/2107.11144).

***Feel free to contact us if you have any questions!***
