/**
 * Copyright (c) 2007-2009 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, and the authors indicated in the @author tags
 * 
 * This file is part of SMaRt.
 * 
 * SMaRt is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * SMaRt is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with SMaRt.  If not, see <http://www.gnu.org/licenses/>.
 */

package navigators.smart.tom.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import org.apache.commons.codec.binary.Base64;

/**
 * Utility class used to generate a key pair for some process id on
 * config/keys/publickey<id> and config/keys/privatekey<id>
 *
 */
public class RSAKeyPairGenerator {
    
    /** Creates a new instance of KeyPairGenerator */
    public RSAKeyPairGenerator() {
    }

    /**
     * Generate the key pair for the process with id = <id> and put it on the
     * files config/keys/publickey<id> and config/keys/privatekey<id>
     *
     * @param id the id of the process to generate key
     * @throws Exception something goes wrong when writing the files
     */
    public void run(int id) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(1024);
        KeyPair kp = keyGen.generateKeyPair();
        PublicKey puk = kp.getPublic();
        PrivateKey prk = kp.getPrivate();
        saveToFile(id,puk,prk);
    }
    
    private void saveToFile(int id, PublicKey puk, PrivateKey prk) throws Exception {
        String path = "config"+System.getProperty("file.separator")+"keys"+
                System.getProperty("file.separator");
        
        BufferedWriter w = new BufferedWriter(new FileWriter(path+"publickey"+id,false));
        w.write(getKeyAsString(puk));
        w.flush();
        w.close();
        
        w = new BufferedWriter(new FileWriter(path+"privatekey"+id,false));
        w.write(getKeyAsString(prk));
        w.flush();
        w.close();
    }
    
    
    private String getKeyAsString(Key key) {
        byte[] keyBytes = key.getEncoded();

        return Base64.encodeBase64String(keyBytes);
    }

    public static void main(String[] args){
        try{
            new RSAKeyPairGenerator().run(Integer.parseInt(args[0]));
        }catch(Exception e){
            System.err.println("Use: RSAKeyPairGenerator <id>");
        }
    }

}
