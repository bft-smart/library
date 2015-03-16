# Introduction #

BFT-SMaRt is a replication library written in Java. It implements state machine replication. It is designed to tolerate Byzantine faults, while still being highly efficient - even if some replicas are faulty. In this page we describe how this library works, in a high level of abstraction. More details can be found in the [technical report](http://docs.di.fc.ul.pt/handle/10455/6897) describing the system.

# State Machine Replication (SMR) #

SMR is a replication technique where an arbitrary number of clients issue commands to a set of servers dubbed **replicas**. These replicas implement a **stateful service** that changes its state after processing client commands, and sends replies to the clients that issued them.

The goal of this technique is to make the state at each replica to evolve in a consistent way, thus making the service completely and accurately replicated at each replica. In order to achieve this behavior, it is necessary to satisfy three properties:

  1. Replicas only apply deterministic changes to the state;
  1. All replicas start with the same state;
  1. All replicas execute the same sequence of operations.

The first two requirements can be fulfilled without any special library, but the last one requires the replicas to communicate among themselfs, which demands the execution of a complex **agreement protocol**, in order to guarantee that the commands are executed in the same order across all replicas.

BFT-SMaRt provides this agreement protocol, assuming that the programmer ensures that the application using BFT-SMaRt fulfills the first 2 requirements. The state machine replication protocol implemented by BFT-SMaRt is described in [this paper](http://www.navigators.di.fc.ul.pt/wiki/Publication:Joaosousa2011). The core of such solution makes use of the BFT version of the consensus protocol described in [this paper](http://www.zurich.ibm.com/~cca/papers/pax.pdf).

# Byzantine Fault Tolerance (BFT) #

BFT-SMaRt is designed to tolerate Byzantine faults. A **Byzantine fault** can be any deviation from the expected behavior of a replica (as opposed to crash faults, where a replica can only fail by stopping its execution). Byzantine faults can happen due to either software bugs, or even due to a skilled adversary that manages to gain control of replicas, thus controlling theirs behavior. Such replicas are therefore addressed as as **malicious replicas**.

Besides Byzantine faults, BFT-SMaRt is also designed to tolerate denial of service attacks (DoS). This type of attack can subvert the time that computations and communications take to complete, thus making it impossible to correctly distinguish a crashed/faulty replica from a delayed replica.

However, in order for such agreement protocol to be able to provide correct replication, the number of malicious replicas needs to be bounded. Furthermore, it is also necessary that the system eventually enters a period where all computation and communication are performed within the expected  time bounds.

Given all aforementioned assumptions, for BFT-SMaRt to execute correctly, less than a third of all replicas of the service can be faulty at any moment. Hence, the total number of replicas **N** must be N >= 3f + 1, where **f** is the maximum number of faulty replicas.

# State Transfer #

It is expected that in real systems replicas will fail. But it is also convenient that they will be fixed and put back in the system. The problem is, the recovered replicas might lose their state, and therefore not be able to participate in the replication. The state transfer protocol enables addresses this issue by enabling the recovered replica to ask the other ones the latest state.

# Reconfiguration #

Systems can either be static or dynamic. In a static system, the same set of processes remains unchanged through its entire lifespan. On the other hand, a dynamic system enables other processes to enter the system, and also leave it. BFT-SMaRt implements an experimental protocol that enables replicas to either join or leave the system during execution. Such protocol is possible with a special client dubbed "Trusted Third Party" (TTP) that commands the insertion and removal of replicas.