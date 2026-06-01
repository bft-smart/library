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

import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutput;

/**
 * Thin {@link ObjectOutput} adapter over a plain {@link DataOutput}. It lets the
 * binary {@code writeExternal(ObjectOutput)} methods of the project's
 * {@link java.io.Externalizable} types be invoked without the
 * {@code ObjectOutputStream} envelope (stream header + reflective class
 * descriptors). {@link #writeObject(Object)} is unsupported on purpose: the
 * converted classes write only primitive/length-prefixed data.
 */
final class DataObjectOutput implements ObjectOutput {

    private final DataOutput out;

    DataObjectOutput(DataOutput out) {
        this.out = out;
    }

    @Override
    public void writeObject(Object obj) {
        throw new UnsupportedOperationException(
                "writeObject is not supported by the binary codec; serialize fields explicitly");
    }

    @Override public void write(int b) throws IOException { out.write(b); }
    @Override public void write(byte[] b) throws IOException { out.write(b); }
    @Override public void write(byte[] b, int off, int len) throws IOException { out.write(b, off, len); }
    @Override public void writeBoolean(boolean v) throws IOException { out.writeBoolean(v); }
    @Override public void writeByte(int v) throws IOException { out.writeByte(v); }
    @Override public void writeShort(int v) throws IOException { out.writeShort(v); }
    @Override public void writeChar(int v) throws IOException { out.writeChar(v); }
    @Override public void writeInt(int v) throws IOException { out.writeInt(v); }
    @Override public void writeLong(long v) throws IOException { out.writeLong(v); }
    @Override public void writeFloat(float v) throws IOException { out.writeFloat(v); }
    @Override public void writeDouble(double v) throws IOException { out.writeDouble(v); }
    @Override public void writeBytes(String s) throws IOException { out.writeBytes(s); }
    @Override public void writeChars(String s) throws IOException { out.writeChars(s); }
    @Override public void writeUTF(String s) throws IOException { out.writeUTF(s); }

    @Override public void flush() { /* underlying stream is flushed by the caller */ }
    @Override public void close() { /* underlying stream is closed by the caller */ }
}
