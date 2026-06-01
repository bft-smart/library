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
package bftsmart.benchmark;

import bftsmart.consensus.TimestampValuePair;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.consensus.messages.MessageFactory;
import bftsmart.tom.util.io.StateCodecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Microbenchmark for the consensus and leader-change serialization that moved off
 * reflective Java serialization onto the binary codec.
 *
 * <ul>
 *   <li><b>sign</b> &mdash; the per-consensus signable bytes of a {@link ConsensusMessage}
 *       (was {@code ObjectOutputStream.writeObject(cm)} in Acceptor / LCManager just to
 *       obtain bytes to sign): OLD reflective vs NEW
 *       {@link StateCodecs#consensusMessageSignableBytes};</li>
 *   <li><b>proofSet</b> &mdash; a {@code Set<ConsensusMessage>} (forwarded-decision /
 *       {@code CertifiedDecision} proof): OLD {@code writeObject(set)} vs NEW
 *       {@link StateCodecs#writeConsensusMessageSet};</li>
 *   <li><b>writeSet</b> &mdash; a {@code Set<TimestampValuePair>} (COLLECT write-set):
 *       OLD {@code writeObject(set)} vs NEW
 *       {@link StateCodecs#writeTimestampValuePairSet}.</li>
 * </ul>
 *
 * Reports encode time, decode time and size as medians over many iterations after warmup.
 */
public final class MessageSerializationBenchmark {

    private static final Random RND = new Random(2024);

    private MessageSerializationBenchmark() {
    }

    private static byte[] rnd(int n) {
        byte[] b = new byte[n];
        RND.nextBytes(b);
        return b;
    }

    private static ConsensusMessage accept(int sender, byte[] value, int sigLen) {
        ConsensusMessage cm = new ConsensusMessage(MessageFactory.ACCEPT, 100, 0, sender, value);
        cm.setProof(rnd(sigLen)); // byte[] signature, as on the real proof path
        return cm;
    }

    private static Set<ConsensusMessage> proofSet(int n, int sigLen) {
        byte[] value = rnd(32);
        Set<ConsensusMessage> set = new HashSet<>();
        for (int i = 0; i < n; i++) {
            set.add(accept(i, value, sigLen));
        }
        return set;
    }

    private static Set<TimestampValuePair> writeSet(int n, int valLen) {
        Set<TimestampValuePair> set = new HashSet<>();
        for (int i = 0; i < n; i++) {
            set.add(new TimestampValuePair(i, rnd(valLen)));
        }
        return set;
    }

    private static byte[] javaSer(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 12);
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(o);
        oos.flush();
        return bos.toByteArray();
    }

    private static Object javaDeser(byte[] b) throws Exception {
        return new ObjectInputStream(new ByteArrayInputStream(b)).readObject();
    }

    private static long median(long[] a) {
        long[] c = a.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }

    private interface Op {
        Object run() throws Exception;
    }

    private static long timeMed(Op op, int warmup, int iters) throws Exception {
        for (int i = 0; i < warmup; i++) {
            op.run();
        }
        long[] t = new long[iters];
        for (int i = 0; i < iters; i++) {
            long s = System.nanoTime();
            op.run();
            t[i] = System.nanoTime() - s;
        }
        return median(t);
    }

    private static void row(String name, Op oldEnc, Op newEnc, Op oldDec, Op newDec,
                            int oldSize, int newSize, int warmup, int iters) throws Exception {
        long oe = timeMed(oldEnc, warmup, iters);
        long ne = timeMed(newEnc, warmup, iters);
        long od = timeMed(oldDec, warmup, iters);
        long nd = timeMed(newDec, warmup, iters);
        System.out.printf(Locale.US,
                "%-28s | enc %9.4f -> %9.4f us (x%5.2f) | dec %9.4f -> %9.4f us (x%5.2f) | size %,8d -> %,8d B (x%.2f)%n",
                name,
                oe / 1e3, ne / 1e3, (double) oe / ne,
                od / 1e3, nd / 1e3, (double) od / nd,
                oldSize, newSize, (double) oldSize / newSize);
    }

    private static void signScenario(int sigLen, int warmup, int iters) throws Exception {
        // signer's cm has no proof yet (proof is set after signing)
        final ConsensusMessage cm = new ConsensusMessage(MessageFactory.ACCEPT, 100, 0, 1, rnd(32));
        byte[] oldBytes = javaSer(cm);
        byte[] newBytes = StateCodecs.consensusMessageSignableBytes(cm);
        row("sign 1 CM (val32)",
                () -> javaSer(cm),
                () -> StateCodecs.consensusMessageSignableBytes(cm),
                () -> javaDeser(oldBytes),
                () -> StateCodecs.consensusMessageSignableBytes(cm), // no decode needed for signing; show encode-equivalent
                oldBytes.length, newBytes.length, warmup, iters);
    }

    private static void proofSetScenario(int n, int sigLen, int warmup, int iters) throws Exception {
        final Set<ConsensusMessage> set = proofSet(n, sigLen);
        byte[] oldBytes = javaSer(set);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StateCodecs.writeConsensusMessageSet(set, new DataOutputStream(bos));
        final byte[] newBytes = bos.toByteArray();
        row("proofSet " + n + "x CM (sig" + sigLen + ")",
                () -> javaSer(set),
                () -> { ByteArrayOutputStream b = new ByteArrayOutputStream();
                        StateCodecs.writeConsensusMessageSet(set, new DataOutputStream(b)); return b.toByteArray(); },
                () -> javaDeser(oldBytes),
                () -> StateCodecs.readConsensusMessageSet(new DataInputStream(new ByteArrayInputStream(newBytes))),
                oldBytes.length, newBytes.length, warmup, iters);
    }

    private static void writeSetScenario(int n, int valLen, int warmup, int iters) throws Exception {
        final Set<TimestampValuePair> set = writeSet(n, valLen);
        byte[] oldBytes = javaSer(set);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        StateCodecs.writeTimestampValuePairSet(set, new DataOutputStream(bos));
        final byte[] newBytes = bos.toByteArray();
        row("writeSet " + n + "x TVP (val" + valLen + ")",
                () -> javaSer(set),
                () -> { ByteArrayOutputStream b = new ByteArrayOutputStream();
                        StateCodecs.writeTimestampValuePairSet(set, new DataOutputStream(b)); return b.toByteArray(); },
                () -> javaDeser(oldBytes),
                () -> StateCodecs.readTimestampValuePairSet(new DataInputStream(new ByteArrayInputStream(newBytes))),
                oldBytes.length, newBytes.length, warmup, iters);
    }

    public static void main(String[] args) throws Exception {
        int warmup = 50;
        int iters = 200;
        System.out.println("JVM: " + System.getProperty("java.version")
                + "  warmup=" + warmup + " iters=" + iters + " (median reported)");
        System.out.println("OLD = Java reflective serialization; NEW = binary codec. x = old/new (>1 ⇒ NEW better)\n");

        // per-consensus signable bytes
        signScenario(256, warmup, iters);

        // forwarded-decision / CertifiedDecision proof sets (2f+1 ACCEPTs, RSA-2048 sigs = 256 B)
        proofSetScenario(3, 256, warmup, iters);    // f=1
        proofSetScenario(7, 256, warmup, iters);    // f=3
        proofSetScenario(21, 256, warmup, iters);   // f=10
        proofSetScenario(100, 256, warmup, iters);  // large

        // COLLECT write-set (leader change)
        writeSetScenario(10, 128, warmup, iters);
        writeSetScenario(100, 128, warmup, iters);
    }
}
