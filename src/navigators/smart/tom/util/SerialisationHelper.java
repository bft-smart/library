/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class SerialisationHelper {

    public static void writeByteArray(byte[] array, DataOutput out) throws IOException{
        out.writeInt(array.length);
        out.write(array);
    }

    public static byte[] readByteArray (DataInput in) throws IOException{
        int len = in.readInt();
        byte[] ret = new byte[len];
        in.readFully(ret);
        return ret;
    }

    public static void writeObject(Object content, DataOutput out) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(content);
        oos.flush();
        writeByteArray(baos.toByteArray(), out);
    }

    public static Object readObject(DataInput in) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(readByteArray(in));
        ObjectInputStream ois = new ObjectInputStream(bais);
        return ois.readObject();
    }

    public static void writeString(String op, DataOutput out) throws IOException {
        out.writeInt(op.length());
        out.writeChars(op);
    }

    public static String readString(DataInput in) throws IOException{
        int len = in.readInt();
        char[] strchar = new char[len];
        for(int i = 0;i < len; i++){
            strchar[i] = in.readChar();
        }
        return new String(strchar);
    }
}
