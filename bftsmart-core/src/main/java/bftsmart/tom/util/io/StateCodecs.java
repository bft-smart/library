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
package bftsmart.tom.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.views.View;
import bftsmart.statemanagement.ApplicationState;
import bftsmart.statemanagement.durability.CSTState;
import bftsmart.tom.MessageContext;
import bftsmart.tom.core.messages.TOMMessage;
import bftsmart.tom.core.messages.TOMMessageType;
import bftsmart.tom.server.defaultservices.CommandsInfo;
import bftsmart.tom.server.defaultservices.DefaultApplicationState;

/**
 * Central registry of custom binary {@link BinaryCodec}s for the state-transfer
 * object graph, plus the entry points used by the state-transfer and durable-log
 * machinery.
 *
 * <p>This replaces the previous use of Java's reflective serialization
 * (ObjectOutputStream / ObjectInputStream) for application state, which carried a
 * heavy CPU and size overhead (class descriptors, handles, reflection) and was the
 * documented performance bottleneck of the durable services. The whole graph is
 * now written with explicit, length-prefixed binary fields:</p>
 *
 * <pre>
 *   ApplicationState (polymorphic, tagged)
 *     |- DefaultApplicationState
 *     |- CSTState
 *          |- CommandsInfo[]
 *               |- byte[][] commands
 *               |- MessageContext[]
 *                    |- Set&lt;ConsensusMessage&gt; proof
 *                    |- TOMMessage firstInBatch
 * </pre>
 *
 * <p>Polymorphic dispatch for {@link ApplicationState} is handled through a small
 * tag registry so that new state implementations can be plugged in without changing
 * the call sites.</p>
 */
public final class StateCodecs {

    private StateCodecs() {
    }

    // ------------------------------------------------------------------
    // ApplicationState polymorphic registry
    // ------------------------------------------------------------------

    private static final byte TAG_NULL = 0;
    private static final byte TAG_DEFAULT_APP_STATE = 1;
    private static final byte TAG_CST_STATE = 2;

    private static final Map<Class<?>, Byte> TAG_BY_CLASS = new HashMap<>();
    private static final Map<Byte, BinaryCodec<? extends ApplicationState>> CODEC_BY_TAG = new HashMap<>();

    private static <T extends ApplicationState> void register(byte tag, Class<T> type, BinaryCodec<T> codec) {
        TAG_BY_CLASS.put(type, tag);
        CODEC_BY_TAG.put(tag, codec);
    }

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /** Writes a (possibly null, possibly polymorphic) {@link ApplicationState} with a leading type tag. */
    public static void writeApplicationState(ApplicationState state, DataOutput out) throws IOException {
        if (state == null) {
            out.writeByte(TAG_NULL);
            return;
        }
        Byte tag = TAG_BY_CLASS.get(state.getClass());
        if (tag == null) {
            throw new IOException("No binary codec registered for application state type " + state.getClass().getName());
        }
        out.writeByte(tag);
        @SuppressWarnings("unchecked")
        BinaryCodec<ApplicationState> codec = (BinaryCodec<ApplicationState>) CODEC_BY_TAG.get(tag);
        codec.encode(state, out);
    }

    /** Reads an {@link ApplicationState} written by {@link #writeApplicationState}. */
    public static ApplicationState readApplicationState(DataInput in) throws IOException {
        byte tag = in.readByte();
        if (tag == TAG_NULL) {
            return null;
        }
        BinaryCodec<? extends ApplicationState> codec = CODEC_BY_TAG.get(tag);
        if (codec == null) {
            throw new IOException("Unknown application state tag " + tag);
        }
        return codec.decode(in);
    }

    /** Serializes a (possibly null) {@link ApplicationState} to a standalone byte array. */
    public static byte[] applicationStateToBytes(ApplicationState state) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        writeApplicationState(state, dos);
        dos.flush();
        return bos.toByteArray();
    }

    /** Reconstructs an {@link ApplicationState} from bytes produced by {@link #applicationStateToBytes}. */
    public static ApplicationState applicationStateFromBytes(byte[] bytes) throws IOException {
        return readApplicationState(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    /** Serializes a single {@link CommandsInfo} (one durable-log entry) to a byte array. */
    public static byte[] commandsInfoToBytes(CommandsInfo info) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        COMMANDS_INFO.encode(info, dos);
        dos.flush();
        return bos.toByteArray();
    }

    /** Reconstructs a {@link CommandsInfo} from bytes produced by {@link #commandsInfoToBytes}. */
    public static CommandsInfo commandsInfoFromBytes(byte[] bytes) throws IOException {
        return COMMANDS_INFO.decode(new DataInputStream(new ByteArrayInputStream(bytes)));
    }

    /** Writes a (possibly null) {@link View} (a leading boolean flags presence). */
    public static void writeView(View view, DataOutput out) throws IOException {
        if (view == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        VIEW.encode(view, out);
    }

    /** Reads a (possibly null) {@link View} written by {@link #writeView}. */
    public static View readView(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return VIEW.decode(in);
    }

    // ------------------------------------------------------------------
    // Concrete codecs
    // ------------------------------------------------------------------

    /** Opaque {@code ConsensusMessage.proof} object: usually a signature ({@code byte[]}). */
    private static final byte PROOF_NULL = 0;
    private static final byte PROOF_BYTES = 1;
    private static final byte PROOF_JAVA = 2; // safety fallback for any other (e.g. MAC vector)

    private static void writeProofObject(Object proof, DataOutput out) throws IOException {
        if (proof == null) {
            out.writeByte(PROOF_NULL);
        } else if (proof instanceof byte[]) {
            out.writeByte(PROOF_BYTES);
            BinaryIO.writeBytes(out, (byte[]) proof);
        } else {
            // Rare path (e.g. MAC vectors): keep correctness with a length-prefixed
            // Java-serialized blob rather than failing.
            out.writeByte(PROOF_JAVA);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(proof);
            }
            BinaryIO.writeBytes(out, bos.toByteArray());
        }
    }

    private static Object readProofObject(DataInput in) throws IOException {
        byte tag = in.readByte();
        switch (tag) {
            case PROOF_NULL:
                return null;
            case PROOF_BYTES:
                return BinaryIO.readBytes(in);
            case PROOF_JAVA:
                byte[] blob = BinaryIO.readBytes(in);
                try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(blob))) {
                    return ois.readObject();
                } catch (ClassNotFoundException e) {
                    throw new IOException("Failed to deserialize fallback proof object", e);
                }
            default:
                throw new IOException("Unknown proof object tag " + tag);
        }
    }

    static final BinaryCodec<ConsensusMessage> CONSENSUS_MESSAGE = new BinaryCodec<ConsensusMessage>() {
        @Override
        public void encode(ConsensusMessage m, DataOutput out) throws IOException {
            out.writeInt(m.getSender());
            out.writeInt(m.getNumber());
            out.writeInt(m.getEpoch());
            out.writeInt(m.getType());
            BinaryIO.writeBytes(out, m.getValue());
            writeProofObject(m.getProof(), out);
        }

        @Override
        public ConsensusMessage decode(DataInput in) throws IOException {
            int sender = in.readInt();
            int number = in.readInt();
            int epoch = in.readInt();
            int paxosType = in.readInt();
            byte[] value = BinaryIO.readBytes(in);
            Object proof = readProofObject(in);
            ConsensusMessage m = new ConsensusMessage(paxosType, number, epoch, sender, value);
            m.setProof(proof);
            return m;
        }
    };

    static final BinaryCodec<TOMMessage> TOM_MESSAGE = new BinaryCodec<TOMMessage>() {
        @Override
        public void encode(TOMMessage m, DataOutput out) throws IOException {
            m.wExternal(out);
        }

        @Override
        public TOMMessage decode(DataInput in) throws IOException {
            TOMMessage m = new TOMMessage();
            try {
                m.rExternal(in);
            } catch (ClassNotFoundException e) {
                throw new IOException("Failed to deserialize TOMMessage", e);
            }
            return m;
        }
    };

    static final BinaryCodec<MessageContext> MESSAGE_CONTEXT = new BinaryCodec<MessageContext>() {
        @Override
        public void encode(MessageContext m, DataOutput out) throws IOException {
            out.writeInt(m.getSender());
            out.writeInt(m.getViewID());
            out.writeInt(m.getType() == null ? -1 : m.getType().ordinal());
            out.writeInt(m.getSession());
            out.writeInt(m.getSequence());
            out.writeInt(m.getOperationId());
            out.writeInt(m.getReplyServer());
            BinaryIO.writeBytes(out, m.getSignature());
            out.writeLong(m.getTimestamp());
            out.writeInt(m.getNumOfNonces());
            out.writeLong(m.getSeed());
            out.writeInt(m.getRegency());
            out.writeInt(m.getLeader());
            out.writeInt(m.getConsensusId());

            Set<ConsensusMessage> proof = m.getProof();
            if (proof == null) {
                out.writeInt(-1);
            } else {
                out.writeInt(proof.size());
                for (ConsensusMessage cm : proof) {
                    CONSENSUS_MESSAGE.encode(cm, out);
                }
            }

            TOMMessage firstInBatch = m.getFirstInBatch();
            if (firstInBatch == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                TOM_MESSAGE.encode(firstInBatch, out);
            }

            out.writeBoolean(m.isNoOp());
        }

        @Override
        public MessageContext decode(DataInput in) throws IOException {
            int sender = in.readInt();
            int viewID = in.readInt();
            int typeOrdinal = in.readInt();
            TOMMessageType type = typeOrdinal < 0 ? null : TOMMessageType.getMessageType(typeOrdinal);
            int session = in.readInt();
            int sequence = in.readInt();
            int operationId = in.readInt();
            int replyServer = in.readInt();
            byte[] signature = BinaryIO.readBytes(in);
            long timestamp = in.readLong();
            int numOfNonces = in.readInt();
            long seed = in.readLong();
            int regency = in.readInt();
            int leader = in.readInt();
            int consensusId = in.readInt();

            int proofSize = in.readInt();
            Set<ConsensusMessage> proof = null;
            if (proofSize >= 0) {
                proof = new HashSet<>(Math.max(2, proofSize));
                for (int i = 0; i < proofSize; i++) {
                    proof.add(CONSENSUS_MESSAGE.decode(in));
                }
            }

            TOMMessage firstInBatch = in.readBoolean() ? TOM_MESSAGE.decode(in) : null;
            boolean noOp = in.readBoolean();

            return new MessageContext(sender, viewID, type, session, sequence, operationId,
                    replyServer, signature, timestamp, numOfNonces, seed, regency, leader,
                    consensusId, proof, firstInBatch, noOp);
        }
    };

    static final BinaryCodec<CommandsInfo> COMMANDS_INFO = new BinaryCodec<CommandsInfo>() {
        @Override
        public void encode(CommandsInfo info, DataOutput out) throws IOException {
            BinaryIO.writeByteMatrix(out, info.commands);
            if (info.msgCtx == null) {
                out.writeInt(-1);
            } else {
                out.writeInt(info.msgCtx.length);
                for (MessageContext ctx : info.msgCtx) {
                    MESSAGE_CONTEXT.encode(ctx, out);
                }
            }
        }

        @Override
        public CommandsInfo decode(DataInput in) throws IOException {
            CommandsInfo info = new CommandsInfo();
            info.commands = BinaryIO.readByteMatrix(in);
            int n = in.readInt();
            if (n >= 0) {
                MessageContext[] ctx = new MessageContext[n];
                for (int i = 0; i < n; i++) {
                    ctx[i] = MESSAGE_CONTEXT.decode(in);
                }
                info.msgCtx = ctx;
            }
            return info;
        }
    };

    /** Writes a {@code CommandsInfo[]}; {@code -1} encodes a {@code null} array. */
    private static void writeCommandsInfoArray(CommandsInfo[] batches, DataOutput out) throws IOException {
        if (batches == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(batches.length);
        for (CommandsInfo info : batches) {
            if (info == null) {
                out.writeBoolean(false);
            } else {
                out.writeBoolean(true);
                COMMANDS_INFO.encode(info, out);
            }
        }
    }

    private static CommandsInfo[] readCommandsInfoArray(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0) {
            return null;
        }
        CommandsInfo[] batches = new CommandsInfo[n];
        for (int i = 0; i < n; i++) {
            batches[i] = in.readBoolean() ? COMMANDS_INFO.decode(in) : null;
        }
        return batches;
    }

    static final BinaryCodec<View> VIEW = new BinaryCodec<View>() {
        @Override
        public void encode(View view, DataOutput out) throws IOException {
            out.writeInt(view.getId());
            out.writeInt(view.getF());
            int[] processes = view.getProcesses();
            out.writeInt(processes.length);
            for (int process : processes) {
                out.writeInt(process);
                InetSocketAddress addr = view.getAddress(process);
                BinaryIO.writeNullableString(out, addr == null ? null : addr.getHostString());
                out.writeInt(addr == null ? -1 : addr.getPort());
            }
        }

        @Override
        public View decode(DataInput in) throws IOException {
            int id = in.readInt();
            int f = in.readInt();
            int n = in.readInt();
            int[] processes = new int[n];
            InetSocketAddress[] addresses = new InetSocketAddress[n];
            for (int i = 0; i < n; i++) {
                processes[i] = in.readInt();
                String host = BinaryIO.readNullableString(in);
                int port = in.readInt();
                addresses[i] = host == null ? null : new InetSocketAddress(host, port);
            }
            return new View(id, processes, f, addresses);
        }
    };

    static final BinaryCodec<DefaultApplicationState> DEFAULT_APP_STATE = new BinaryCodec<DefaultApplicationState>() {
        @Override
        public void encode(DefaultApplicationState s, DataOutput out) throws IOException {
            BinaryIO.writeBytes(out, s.getSerializedState());
            BinaryIO.writeBytes(out, s.getStateHash());
            out.writeInt(s.getLastCID());
            out.writeBoolean(s.hasState());

            TreeMap<Integer, TOMMessage> lastReplies = s.getLastReplies();
            if (lastReplies == null) {
                out.writeInt(-1);
            } else {
                out.writeInt(lastReplies.size());
                for (Map.Entry<Integer, TOMMessage> e : lastReplies.entrySet()) {
                    out.writeInt(e.getKey());
                    TOM_MESSAGE.encode(e.getValue(), out);
                }
            }
            BinaryIO.writeBytes(out, s.getLastRepliesHash());

            writeCommandsInfoArray(s.getMessageBatches(), out);
            out.writeInt(s.getLastCheckpointCID());
            BinaryIO.writeBytes(out, s.getLogHash());
            out.writeInt(s.getPid());
        }

        @Override
        public DefaultApplicationState decode(DataInput in) throws IOException {
            byte[] state = BinaryIO.readBytes(in);
            byte[] stateHash = BinaryIO.readBytes(in);
            int lastCID = in.readInt();
            boolean hasState = in.readBoolean();

            int repliesSize = in.readInt();
            TreeMap<Integer, TOMMessage> lastReplies = null;
            if (repliesSize >= 0) {
                lastReplies = new TreeMap<>();
                for (int i = 0; i < repliesSize; i++) {
                    int key = in.readInt();
                    lastReplies.put(key, TOM_MESSAGE.decode(in));
                }
            }
            byte[] lastRepliesHash = BinaryIO.readBytes(in);

            CommandsInfo[] batches = readCommandsInfoArray(in);
            int lastCheckpointCID = in.readInt();
            byte[] logHash = BinaryIO.readBytes(in);
            int pid = in.readInt();

            return new DefaultApplicationState(batches, lastCheckpointCID, lastCID, state, stateHash,
                    pid, lastReplies, lastRepliesHash, logHash, hasState);
        }
    };

    static final BinaryCodec<CSTState> CST_STATE = new BinaryCodec<CSTState>() {
        @Override
        public void encode(CSTState s, DataOutput out) throws IOException {
            BinaryIO.writeBytes(out, s.getSerializedState());
            BinaryIO.writeBytes(out, s.getHashCheckpoint());
            writeCommandsInfoArray(s.getLogLower(), out);
            BinaryIO.writeBytes(out, s.getHashLogLower());
            writeCommandsInfoArray(s.getLogUpper(), out);
            BinaryIO.writeBytes(out, s.getHashLogUpper());
            out.writeInt(s.getCheckpointCID());
            out.writeInt(s.getLastCID());
            out.writeInt(s.getPid());
        }

        @Override
        public CSTState decode(DataInput in) throws IOException {
            byte[] state = BinaryIO.readBytes(in);
            byte[] hashCheckpoint = BinaryIO.readBytes(in);
            CommandsInfo[] logLower = readCommandsInfoArray(in);
            byte[] hashLogLower = BinaryIO.readBytes(in);
            CommandsInfo[] logUpper = readCommandsInfoArray(in);
            byte[] hashLogUpper = BinaryIO.readBytes(in);
            int checkpointCID = in.readInt();
            int lastCID = in.readInt();
            int pid = in.readInt();
            return new CSTState(state, hashCheckpoint, logLower, hashLogLower, logUpper, hashLogUpper,
                    checkpointCID, lastCID, pid);
        }
    };

    // Registered after the codec fields are initialized (static initializers run in textual order).
    static {
        register(TAG_DEFAULT_APP_STATE, DefaultApplicationState.class, DEFAULT_APP_STATE);
        register(TAG_CST_STATE, CSTState.class, CST_STATE);
    }
}
