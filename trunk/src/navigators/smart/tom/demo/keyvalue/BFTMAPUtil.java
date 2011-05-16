/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo.keyvalue;
import java.util.HashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Scanner;

import java.util.logging.Level;
import java.util.logging.Logger;
import navigators.smart.tom.ServiceProxy;
import java.io.Console;
import java.io.Serializable;

/**
 *
 * @author sweta
 */
public class BFTMAPUtil implements Serializable {

    static final int TAB_CREATE = 1;
    static final int TAB_REMOVE = 2;
    static final int SIZE_TABLE=3;
    static final int TAB_CREATE_CHECK = 10;
    static final int PUT = 4;
    static final int GET = 5;
    static final int SIZE = 6;
    static final int REMOVE = 7;
    static final int CHECK = 8;
    static final int GET_TABLE = 9;
    
    //private HashMap<String,byte[]> info = null;
    private HashMap<String,HashMap<String,byte[]>> info1 = null;
    //private HashMap<String,byte[]> info = null;

   public BFTMAPUtil() {

          info1=new HashMap<String,HashMap<String,byte[]>>();

    }
   
   public HashMap<String,byte[]> addTable(String key,HashMap data) {
       return info1.put(key, data);
   }

   public byte[] addData(String tableName,String key,byte[] value) {
       System.out.println("Table name"+tableName);
       System.out.println("Table id"+key);
       System.out.println("Value"+ new String(value));
       HashMap<String,byte[]> info= info1.get(tableName);
       if (info == null) System.out.println("This is null");
       System.out.println("Table"+info);
       byte[] ret = info.put(key,value);
      // byte[] ret = info.get(key);
     //  System.out.println("Value"+ new String(ret));
       return ret;
   }

   public HashMap<String,byte[]> getName(String id) {

          return info1.get(id);
     }

   public byte[] getEntry(String tableName, String id) {
          System.out.println("Table name"+tableName);
          System.out.println("Table id"+id);
          HashMap<String,byte[]> info= info1.get(tableName);
          System.out.println("Table"+info);
          return info.get(id);
     }

   public int getSizeofTable() {

          return info1.size();

     }

   public int getSize(String tableName) {

       HashMap<String,byte[]> info= info1.get(tableName);
       return info.size();

   }

   public HashMap<String,byte[]> removeTable(String tableName) {

          return info1.remove(tableName);

    }

   public byte[] removeEntry(String tableName,String key) {
          HashMap<String,byte[]> info= info1.get(tableName);
          return info.remove(key);

    }
}
