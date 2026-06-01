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

import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultApplicationState;
import bftsmart.tom.util.io.StateCodecs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Microbenchmark comparing application-state serialization between:
 * <ul>
 *   <li><b>OLD</b> &mdash; Java default object serialization
 *       ({@link ObjectOutputStream}/{@link ObjectInputStream}); still available
 *       because the state classes remain {@code Serializable}; and</li>
 *   <li><b>NEW</b> &mdash; the custom binary codec {@link StateCodecs}.</li>
 * </ul>
 * Both paths run on identical state object graphs. For each scenario the
 * benchmark reports serialize time, deserialize time and payload size, all as
 * medians over many iterations after warmup.
 *
 * <p>Run with: {@code java -cp <classpath> bftsmart.benchmark.StateSerializationBenchmark}</p>
 */
public final class StateSerializationBenchmark {

    private static final Random RND = new Random(12345);

    private StateSerializationBenchmark() {
    }

    private static byte[] rnd(int n) {
        byte[] b = new byte[n];
        RND.nextBytes(b);
        return b;
    }

    private static MessageContext makeCtx(int sender, int sigLen, int proofCount, int proofSigLen, int cmdLen) {
        Set<ConsensusMessage> proof = new HashSet<>();
        for (int i = 0; i < proofCount; i++) {
            ConsensusMessage cm = new ConsensusMessage(2, 7, 1, i, rnd(cmdLen));
            cm.setProof(rnd(proofSigLen)); // byte[] signature, as in the real state path
            proof.add(cm);
        }
        TOMMessage first = new TOMMessage(sender, 3, 4, 5, rnd(cmdLen), 11, TOMMessageType.ORDERED_REQUEST);
        return new MessageContext(sender, 11, TOMMessageType.ORDERED_REQUEST, 3, 4, 5, 1,
                rnd(sigLen), 123456789L, 2, 987654321L, 0, 1, 7, proof, first, false);
    }

    private static DefaultApplicationState makeState(int batches, int cmdsPerBatch, int cmdLen,
                                                     int sigLen, int proofCount, int proofSigLen, int ckpStateBytes) {
        CommandsInfo[] log = new CommandsInfo[batches];
        for (int b = 0; b < batches; b++) {
            byte[][] commands = new byte[cmdsPerBatch][];
            MessageContext[] ctx = new MessageContext[cmdsPerBatch];
            for (int c = 0; c < cmdsPerBatch; c++) {
                commands[c] = rnd(cmdLen);
                ctx[c] = makeCtx(c, sigLen, proofCount, proofSigLen, cmdLen);
            }
            log[b] = new CommandsInfo(commands, ctx);
        }
        return new DefaultApplicationState(log, 0, batches, rnd(ckpStateBytes), rnd(32), 0,
                new TreeMap<Integer, TOMMessage>(), rnd(32), rnd(32), true);
    }

    private static byte[] oldSer(ApplicationState s) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(1 << 16);
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(s);
        oos.flush();
        return bos.toByteArray();
    }

    private static ApplicationState oldDeser(byte[] b) throws Exception {
        return (ApplicationState) new ObjectInputStream(new ByteArrayInputStream(b)).readObject();
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

    private static void scenario(String name, DefaultApplicationState st, int warmup, int iters) throws Exception {
        byte[] oldBytes = oldSer(st);
        byte[] newBytes = StateCodecs.applicationStateToBytes(st);
        StateCodecs.applicationStateFromBytes(newBytes); // correctness sanity

        long oldS = timeMed(() -> oldSer(st), warmup, iters);
        long newS = timeMed(() -> StateCodecs.applicationStateToBytes(st), warmup, iters);
        long oldD = timeMed(() -> oldDeser(oldBytes), warmup, iters);
        long newD = timeMed(() -> StateCodecs.applicationStateFromBytes(newBytes), warmup, iters);

        System.out.printf(Locale.US,
                "%-27s | ser %8.3f -> %8.3f ms (x%.2f) | deser %8.3f -> %8.3f ms (x%.2f) | size %,11d -> %,11d B (x%.2f)%n",
                name,
                oldS / 1e6, newS / 1e6, (double) oldS / newS,
                oldD / 1e6, newD / 1e6, (double) oldD / newD,
                oldBytes.length, newBytes.length, (double) oldBytes.length / newBytes.length);
    }

    public static void main(String[] args) throws Exception {
        int warmup = 30;
        int iters = 100;
        System.out.println("JVM: " + System.getProperty("java.version")
                + "  warmup=" + warmup + " iters=" + iters + " (median reported)");
        System.out.println("Legend: x = old/new (>1 means NEW is faster / smaller)\n");

        scenario("A log 1000x1 cmd128 p3s256", makeState(1000, 1, 128, 256, 3, 256, 1024), warmup, iters);
        scenario("B log 200x10 cmd1024 p3s256", makeState(200, 10, 1024, 256, 3, 256, 1024), warmup, iters);
        scenario("C ckp 8MB + log 10x1", makeState(10, 1, 128, 256, 3, 256, 8 * 1024 * 1024), 10, 30);
        scenario("D log 1x1 cmd128 p3s64", makeState(1, 1, 128, 64, 3, 64, 256), warmup, iters);
        scenario("E log 5000x1 cmd128 p3s256", makeState(5000, 1, 128, 256, 3, 256, 1024), 10, 40);
    }
}
