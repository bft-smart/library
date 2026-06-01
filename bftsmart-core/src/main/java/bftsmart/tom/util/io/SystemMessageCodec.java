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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import bftsmart.communication.SystemMessage;
import bftsmart.consensus.messages.ConsensusMessage;
import bftsmart.reconfiguration.VMMessage;
import bftsmart.statemanagement.durability.CSTSMMessage;
import bftsmart.statemanagement.standard.StandardSMMessage;
import bftsmart.tom.core.messages.ForwardedMessage;
import bftsmart.tom.leaderchange.LCMessage;

/**
 * Binary envelope codec for the replica-to-replica transport. It replaces
 * {@code ObjectOutputStream}/{@code ObjectInputStream} on the hot path: a one-byte type
 * tag selects the concrete {@link SystemMessage} subtype, then the message's own
 * {@code Externalizable} {@code writeExternal}/{@code readExternal} is driven over a plain
 * {@link DataOutputStream}/{@link DataInputStream} via {@link DataObjectOutput}/
 * {@link DataObjectInput} — i.e. with no stream header and no reflective class descriptors.
 *
 * <p>Every {@code SystemMessage} sent over the server-to-server channel is covered
 * (consensus, leader change, state-transfer SM messages, forwarded requests, reconfig
 * VM messages). Raw {@code TOMMessage}s travel on the client channel (Netty), not here.</p>
 */
public final class SystemMessageCodec {

    private static final byte T_CONSENSUS = 1;
    private static final byte T_LC = 2;
    private static final byte T_FORWARDED = 3;
    private static final byte T_CST_SM = 4;
    private static final byte T_STD_SM = 5;
    private static final byte T_VM = 6;

    private SystemMessageCodec() {
    }

    public static byte[] toBytes(SystemMessage sm) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(256);
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeByte(tagOf(sm));
        sm.writeExternal(new DataObjectOutput(dos));
        dos.flush();
        return bos.toByteArray();
    }

    public static SystemMessage fromBytes(byte[] data) throws IOException, ClassNotFoundException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        byte tag = dis.readByte();
        SystemMessage sm = instantiate(tag);
        sm.readExternal(new DataObjectInput(dis));
        return sm;
    }

    private static byte tagOf(SystemMessage sm) {
        // Order matters only for readability; these are all concrete leaf types.
        if (sm instanceof ConsensusMessage) return T_CONSENSUS;
        if (sm instanceof LCMessage) return T_LC;
        if (sm instanceof ForwardedMessage) return T_FORWARDED;
        if (sm instanceof CSTSMMessage) return T_CST_SM;
        if (sm instanceof StandardSMMessage) return T_STD_SM;
        if (sm instanceof VMMessage) return T_VM;
        throw new IllegalArgumentException(
                "Unsupported SystemMessage type for binary transport: " + sm.getClass().getName());
    }

    private static SystemMessage instantiate(byte tag) {
        switch (tag) {
            case T_CONSENSUS: return new ConsensusMessage();
            case T_LC: return new LCMessage();
            case T_FORWARDED: return new ForwardedMessage();
            case T_CST_SM: return new CSTSMMessage();
            case T_STD_SM: return new StandardSMMessage();
            case T_VM: return new VMMessage();
            default:
                throw new IllegalArgumentException("Unknown SystemMessage type tag: " + tag);
        }
    }
}
