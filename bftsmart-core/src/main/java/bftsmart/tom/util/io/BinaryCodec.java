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
 * A custom binary codec for a single concrete type {@code T}. Implementations write
 * and read a compact, schema-less binary representation directly over
 * {@link DataOutput}/{@link DataInput}, avoiding the per-object class descriptors and
 * reflection of Java's default serialization.
 *
 * <p>Codecs for the state-transfer object graph are registered in {@link StateCodecs}.</p>
 *
 * @param <T> the concrete type handled by this codec
 */
public interface BinaryCodec<T> {

    /** Serializes {@code obj} to {@code out}. The value is assumed non-null. */
    void encode(T obj, DataOutput out) throws IOException;

    /** Deserializes and reconstructs a {@code T} from {@code in}. */
    T decode(DataInput in) throws IOException;
}
