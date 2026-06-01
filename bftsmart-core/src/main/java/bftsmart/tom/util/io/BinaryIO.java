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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Null-safe, length-prefixed primitives shared by the custom binary state codecs
 * ({@link StateCodecs}). These replace the reflective Java object serialization
 * (ObjectOutputStream/ObjectInputStream) that was previously used for the state
 * transfer and on-disk log/checkpoint of the default durable services.
 *
 * <p>All byte-array reads use {@link DataInput#readFully(byte[])} so that large
 * payloads are always fully read &mdash; this is the bug that historically caused
 * the original {@code Externalizable} attempt in {@code CommandsInfo} to be
 * reverted (a bare {@code in.read(buf)} does not guarantee a full read).</p>
 */
public final class BinaryIO {

    private BinaryIO() {
    }

    /** Writes a byte array prefixed by its length; {@code -1} encodes {@code null}. */
    public static void writeBytes(DataOutput out, byte[] b) throws IOException {
        if (b == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(b.length);
        out.write(b);
    }

    /** Reads a byte array written by {@link #writeBytes}; returns {@code null} for a {@code -1} length. */
    public static byte[] readBytes(DataInput in) throws IOException {
        int len = in.readInt();
        if (len < 0) {
            return null;
        }
        byte[] b = new byte[len];
        in.readFully(b);
        return b;
    }

    /** Writes a (possibly jagged) byte matrix; {@code -1} encodes a {@code null} matrix. */
    public static void writeByteMatrix(DataOutput out, byte[][] m) throws IOException {
        if (m == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(m.length);
        for (byte[] row : m) {
            writeBytes(out, row);
        }
    }

    /** Reads a byte matrix written by {@link #writeByteMatrix}; returns {@code null} for a {@code -1} length. */
    public static byte[][] readByteMatrix(DataInput in) throws IOException {
        int n = in.readInt();
        if (n < 0) {
            return null;
        }
        byte[][] m = new byte[n][];
        for (int i = 0; i < n; i++) {
            m[i] = readBytes(in);
        }
        return m;
    }

    /** Writes a nullable string (a leading boolean flags presence). */
    public static void writeNullableString(DataOutput out, String s) throws IOException {
        if (s == null) {
            out.writeBoolean(false);
            return;
        }
        out.writeBoolean(true);
        out.writeUTF(s);
    }

    /** Reads a nullable string written by {@link #writeNullableString}. */
    public static String readNullableString(DataInput in) throws IOException {
        if (!in.readBoolean()) {
            return null;
        }
        return in.readUTF();
    }
}
