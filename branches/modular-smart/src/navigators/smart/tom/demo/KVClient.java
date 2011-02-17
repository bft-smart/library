/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.demo;
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
import java.util.HashMap;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
/**
 *
 * @author sweta
 */
public class KVClient {


       public static void main(String[] args) throws IOException {
        if(args.length < 1) {
            System.out.println("Usage: java KVClient <process id> ");
            System.exit(-1);
        }

        BFTMap bftMap = new BFTMap(Integer.parseInt(args[0]));

        Console console = System.console();
        Scanner sc = new Scanner(System.in);
        boolean res = false;
        ObjectOutput out=null;


        while(true) {

         System.out.println("select a command : 1. CREATE A NEW TABLE OF TABLES");
         System.out.println("select a command : 2. REMOVE AN EXISTING TABLE OF TABLES");
         System.out.println("select a command : 3. GET THE SIZE OF THE TABLE OF TABLES");
         System.out.println("select a command : 4. PUT VALUES INTO A TABLE");
         System.out.println("select a command : 5. GET VALUES FROM A TABLE");
         System.out.println("select a command : 6. GET THE SIZE OF A TABLE");
         System.out.println("select a command : 7. REMOVE AN EXISTING TABLE");

         int cmd = sc.nextInt();


         switch(cmd) {

             //operations on the table
             case BFTMAPUtil.TAB_CREATE:

                 boolean result4 = false;
                 do {
                    String tableKey = console.readLine("Enter the HashMap name");
                    result4 = bftMap.containsKey(tableKey);
                    if (!result4) {
                       //if the table name does not exist then create the table
                       bftMap.put(tableKey,new HashMap<String,byte[]>());
                    }
                 } while(result4);
                 break;

             case BFTMAPUtil.SIZE_TABLE:
                 //obtain the size of the table of tables.
                 System.out.println("Computing the size of the table");
                 int size=bftMap.size();
                 System.out.println("The size of the table of tables is "+size);
                 break;

             case BFTMAPUtil.TAB_REMOVE:
                 //Remove the table entry
                  boolean result11 = false;
                  HashMap<String, byte[]> info = null;
                  String tableKey5 = null;
                  System.out.println("Executing remove operation on table of tables");
                 do {
                     tableKey5 = console.readLine("Enter the valid table name you want to remove");
                    result11 = bftMap.containsKey(tableKey5);
                                        
                 } while(!result11);
                info = bftMap.remove(tableKey5);
                System.out.println("Table removed");
                break;
             //operations on the hashmap
             case BFTMAPUtil.PUT:
                 System.out.println("Execute put function");
                 boolean result5 = false;
                 String value = null;
                 String tableKey = null;
                 String key = null;
                 do {
                    tableKey = console.readLine("Enter the valid table name in which you want to enter the data");
                    result4 = bftMap.containsKey(tableKey);
                    if (result4) {
                       //if the table name does not exist then create the table
                      key = console.readLine("Enter the key");
                      value = console.readLine("Enter the value");
                    }

                 } while(!result4);
              
                 byte[] b = value.getBytes();
                 byte[] result = bftMap.put1(tableKey,key, b);
                 System.out.println("Result : "+new String(result));
                 break;
             case BFTMAPUtil.GET:
                 System.out.println("Execute get function");
                 boolean result6 = false;
                 boolean result7 = false;
                 String value2 = null;
                 String tableName1 = null;
                 String key1 = null;
                 do {
                 tableName1 = console.readLine("Enter the valid table name from which you want to get the values");

                 result6 = bftMap.containsKey(tableName1);
                 if (result6) {
                     do {
                     key1 = console.readLine("Enter the valid key: ");
                     result7 = bftMap.containsKey1(tableName1, key1);
                     if(result7) {
                         byte[] result1 = bftMap.getEntry(tableName1,key1);
                         System.out.println("The value received from GET is"+new String(result1));
                     }

                     } while(!result7);
                 }
                 } while(!result6);
                 
                 break;
             case BFTMAPUtil.SIZE:
                 System.out.println("Execute get function");
                 boolean result8 = false;
                 String value3 = null;
                 String tableName2 = null;
                 String key2 = null;
                 int size1 = -1;
                 do {
                 tableName2 = console.readLine("Enter the valid table whose size you want to detemine");

                 result8 = bftMap.containsKey(tableName2);
                 if (result8) {
                     size1 = bftMap.size1(tableName2);
                     System.out.println("The size is : "+size1);

                 }
                 } while(!result8);

                 
                 break;
             case BFTMAPUtil.REMOVE:
                 System.out.println("Execute get function");
                 boolean result9 = false;
                 boolean result10 = false;
                 String value4 = null;
                 String tableName3 = null;
                 String key3 = null;
                 do {
                 tableName3 = console.readLine("Enter the valid table name which you want to remove");

                 result9 = bftMap.containsKey(tableName3);
                 if (result9) {
                     do {
                     key3 = console.readLine("Enter the valid key: ");
                     result10 = bftMap.containsKey1(tableName3, key3);
                     if(result10) {
                         byte[] result2 = bftMap.removeEntry(tableName3,key3);
                         System.out.println("The previous value was : "+new String(result2));
                     }

                     } while(!result10);
                 }
                 } while(!result10);

                 
                 break;


         }




       }
       }
}




