/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, Tulio Ribeiro and the authors indicated in the @author tags

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
package bftsmart.tom.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/*
 * Utility class used to generate a key pair for some process id on
 * config/keys/publickey<id> and config/keys/privatekey<id>
 *
 */
public class ECDSAKeyPairGenerator {
    
    /** Creates a new instance of KeyPairGenerator */
    public ECDSAKeyPairGenerator() {
    }

    /**
     * Generate the key pair for the process with id = <id> and put it on the
     * files config/keys/publickey<id> and config/keys/privatekey<id>
     *
     * @param id the id of the process to generate key
     * @throws Exception something goes wrong when writing the files
     */
    public void run(int id, int size) throws Exception {
    	
    	  Security.addProvider(new BouncyCastleProvider());
          
          KeyPairGenerator kpg = KeyPairGenerator.getInstance("ECDSA", "BC");
          kpg.initialize(size);
          KeyPair kp = kpg.genKeyPair();
          PublicKey publicKey = kp.getPublic();
          PrivateKey privateKey = kp.getPrivate();
          System.out.println("KeyPair Private Algorithm: " + kp.getPrivate().getAlgorithm() );
          System.out.println("KeyPair Private Format: " + kp.getPrivate().getFormat() );
          System.out.println("PrivateKey: " + privateKey );
          
          System.out.println("KeyPair Public Algorithm: " + kp.getPublic().getAlgorithm());
          System.out.println("KeyPair Public Format: " + kp.getPublic().getFormat());
          System.out.println("\nPublicKey: " + publicKey );
          
          
          saveToFile(id, publicKey, privateKey);
    }
    
    private void saveToFile(int id, PublicKey publicKey, PrivateKey privateKey) throws Exception {
        String path = "config"+System.getProperty("file.separator")+"keysECDSA"+
                System.getProperty("file.separator");
        
        BufferedWriter w = new BufferedWriter(new FileWriter(path+"publickey"+id,false));
        w.write(getKeyAsString(publicKey));
        w.flush();
        w.close();
        
        w = new BufferedWriter(new FileWriter(path+"privatekey"+id,false));
        w.write(getKeyAsString(privateKey));
        w.flush();
        w.close();
    }
    
    private String getKeyAsString(Key key) {
        byte[] keyBytes = key.getEncoded();
        return Base64.encodeBase64String(keyBytes);
    }

    
   
    public static void main(String[] args){
        try{
            new ECDSAKeyPairGenerator().run(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
        }catch(Exception e){
            System.err.println("Use: ECDSAKeyPairGenerator <id> <key size 256|384|521>");
            e.printStackTrace();
        }
    }

}
