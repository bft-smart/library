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
import java.io.IOException;
import java.io.ObjectInput;

/**
 * Thin {@link ObjectInput} adapter over a plain {@link DataInput}, the read-side
 * counterpart of {@link DataObjectOutput}. {@link #readObject()} and the
 * {@code InputStream}-style methods are unsupported on purpose: the converted
 * {@link java.io.Externalizable} types read only primitive/length-prefixed data.
 */
final class DataObjectInput implements ObjectInput {

    private final DataInput in;

    DataObjectInput(DataInput in) {
        this.in = in;
    }

    @Override
    public Object readObject() {
        throw new UnsupportedOperationException(
                "readObject is not supported by the binary codec; read fields explicitly");
    }

    // Backed by a DataInput, so the InputStream-style reads are fulfilled via readFully
    // (the payloads are length-prefixed and fully present), letting Externalizable types
    // that call read(byte[], ...) — e.g. ConsensusMessage — be driven without ObjectInputStream.
    @Override public int read() throws IOException {
        try {
            return in.readUnsignedByte();
        } catch (java.io.EOFException eof) {
            return -1;
        }
    }
    @Override public int read(byte[] b) throws IOException { in.readFully(b); return b.length; }
    @Override public int read(byte[] b, int off, int len) throws IOException { in.readFully(b, off, len); return len; }
    @Override public long skip(long n) { throw new UnsupportedOperationException(); }
    @Override public int available() { throw new UnsupportedOperationException(); }
    @Override public void close() { /* underlying stream is closed by the caller */ }

    @Override public void readFully(byte[] b) throws IOException { in.readFully(b); }
    @Override public void readFully(byte[] b, int off, int len) throws IOException { in.readFully(b, off, len); }
    @Override public int skipBytes(int n) throws IOException { return in.skipBytes(n); }
    @Override public boolean readBoolean() throws IOException { return in.readBoolean(); }
    @Override public byte readByte() throws IOException { return in.readByte(); }
    @Override public int readUnsignedByte() throws IOException { return in.readUnsignedByte(); }
    @Override public short readShort() throws IOException { return in.readShort(); }
    @Override public int readUnsignedShort() throws IOException { return in.readUnsignedShort(); }
    @Override public char readChar() throws IOException { return in.readChar(); }
    @Override public int readInt() throws IOException { return in.readInt(); }
    @Override public long readLong() throws IOException { return in.readLong(); }
    @Override public float readFloat() throws IOException { return in.readFloat(); }
    @Override public double readDouble() throws IOException { return in.readDouble(); }
    @Override @SuppressWarnings("deprecation") public String readLine() throws IOException { return in.readLine(); }
    @Override public String readUTF() throws IOException { return in.readUTF(); }
}
