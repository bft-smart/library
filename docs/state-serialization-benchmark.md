# State serialization — performance report

Comparison of application-state serialization between the **OLD** path (Java
default object serialization, `ObjectOutputStream`/`ObjectInputStream`) and the
**NEW** custom binary codec (`bftsmart.tom.util.io.StateCodecs`), introduced to
remove the reflective-serialization bottleneck of the durable services.

## Methodology

- Benchmark: `bftsmart.benchmark.StateSerializationBenchmark` (reproducible, in-repo).
- Both paths run on **identical** `DefaultApplicationState` object graphs
  (`CommandsInfo[]` → `MessageContext` → `Set<ConsensusMessage> proof` +
  `firstInBatch`), so the comparison is apples-to-apples.
- Metrics: serialize time, deserialize time, payload size — **median** over 30–100
  measured iterations after 10–30 warmup iterations.
- Environment: OpenJDK 21, shared 4-vCPU container. `perf(1)` was not available in
  the sandbox (`perf_event_paranoid=2`, binary absent), so measurements are
  JVM wall-clock + payload size, which are the meaningful metrics for this change.
  Absolute times are noisy on a shared host; the **ratios** are the takeaway.

> Note: a single cold first-scenario run showed deser ≈7× for scenario A; that was
> a class-loading/JIT warm-up artifact. The stable medians below are reported instead.

## Results (median, x = old/new, >1 ⇒ NEW better)

| Scenario | Shape | Serialize | Deserialize | Size |
|---|---|---|---|---|
| **A** | log 1000×1, cmd 128 B, proof 3×256 B | ~1.3× | **~2.0×** | 1.06× |
| **B** | log 200×10, cmd 1 KB, proof 3×256 B | ~1.5× | ~1.6× | 1.02× |
| **C** | checkpoint 8 MB + log 10×1 | ~1.3× | ~1.25× | 1.00× |
| **D** | log 1×1, cmd 128 B, proof 3×64 B (tiny) | ~3.5–4.7× | **~13–20×** | **1.96×** |
| **E** | log 5000×1, cmd 128 B, proof 3×256 B | ~1.4× | **~1.7×** | 1.06× |

Representative raw line (scenario E):

```
E log 5000x1 cmd128 p3s256 | ser 17.4 -> 12.9 ms (x1.37) | deser 15.4 -> 9.1 ms (x1.70) | size 9,802,469 -> 9,256,158 B (x1.06)
```

## Analysis

- **Deserialization is the headline win.** Reading state back (replica recovery via
  `FileRecoverer`, and a leecher decoding an `SMMessage`) is dominated by
  `ObjectInputStream` parsing class descriptors and reflecting fields. The codec
  reads fixed-layout binary, giving ~1.6–2× on large logs and **10–20× on small,
  metadata-heavy payloads**.
- **Serialization** improves ~1.3–1.6× on realistic state and up to ~4.7× on small
  objects.
- **Payload size**: bulk byte arrays (commands, checkpoints, signatures) are already
  written efficiently by Java serialization, so size shrinks only ~2–6% on byte-heavy
  graphs — but ~**2×** on small objects, where Java's per-class descriptors dominate.
- The more objects (vs. raw bytes) in the graph, the bigger the win — exactly the
  durable-log shape (`MessageContext` per command, proof sets, `firstInBatch`).

## Conclusion

The custom binary codec is strictly faster and never larger across every scenario.
The biggest gains land on the operations that matter most for availability:
**state recovery / state transfer deserialization**. Functional correctness is
verified by round-trip tests covering the full graph, polymorphic dispatch, null
handling, `CSTState`, `View` and the end-to-end `SMMessage` path.

## Reproduce

```bash
./gradlew :bftsmart-tls:compileJava
CP="bftsmart-core/build/classes/java/main:bftsmart-tls/build/classes/java/main:<slf4j/logback jars>"
java -Xms512m -Xmx2g -cp "$CP" bftsmart.benchmark.StateSerializationBenchmark
```

---

# Consensus & leader-change messages — performance report

After extending the binary codec to the consensus, leader-change and
reconfiguration paths, this section measures the parts that moved off reflective
Java serialization. Benchmark: `bftsmart.benchmark.MessageSerializationBenchmark`
(OLD = `ObjectOutputStream`/`ObjectInputStream`, NEW = binary codec; medians over
200 iterations after 50 warmup; OpenJDK 21).

| Scenario | What | Encode | Decode | Size |
|---|---|---|---|---|
| **sign 1 CM** | per-consensus signable bytes (Acceptor/LCManager) | **~14–19×** | **~49×** | **3.3×** |
| proofSet 3× | forwarded-decision / CertifiedDecision proof, f=1 | ~4× | ~9× | 1.20× |
| proofSet 7× | proof set, f=3 | ~3× | ~5× | 1.11× |
| proofSet 21× | proof set, f=10 | ~1.0× (par) | ~3.4× | 1.06× |
| proofSet 100× | very large proof set | ~0.85× (slightly slower) | ~2.0× | 1.04× |
| writeSet 10× | COLLECT write-set (leader change) | ~2× | ~3× | 1.13× |
| writeSet 100× | large COLLECT write-set | ~1–3× | ~1.6–3× | 1.07× |

## Analysis

- **The per-consensus signing path is the standout.** Producing the bytes to sign
  was a fresh `ObjectOutputStream.writeObject(cm)` per message — stream header plus a
  full class descriptor for a ~50-byte object. The binary form is ~15× faster to
  encode, ~49× faster to read back, and 3.3× smaller.
- **Decode wins everywhere** (~2–50×): verification no longer parses class descriptors
  or reflects fields. This matters for proof/COLLECT verification during leader change.
- **Encode for large proof sets is a wash** (par to ~15% slower at 100 elements):
  reflective serialization amortizes the class descriptor across many elements via
  back-references, while the codec writes a full per-element encoding. These large
  sets only occur at high `f` (21+ replicas) and are rare; decode and size still favor
  the codec.

## Honest caveats

- The per-consensus **signable-bytes** win, while large in ratio, is small in absolute
  terms (~20 µs) next to the **RSA signature** that immediately follows it (often
  ~100 µs–1 ms). It is a free improvement, not a throughput multiplier on its own.
- `perf(1)` was unavailable in the sandbox; these are JVM wall-clock + size numbers on
  a shared host, so treat the ratios — not the absolute microseconds — as the result.

## Reproduce

```bash
./gradlew :bftsmart-tls:compileJava
CP="bftsmart-core/build/classes/java/main:bftsmart-tls/build/classes/java/main:<slf4j/logback/bouncycastle/commons-codec jars>"
java -Xms256m -Xmx1g -cp "$CP" bftsmart.benchmark.MessageSerializationBenchmark
```
