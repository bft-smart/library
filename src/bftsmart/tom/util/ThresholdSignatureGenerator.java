/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.util;

import bftsmart.reconfiguration.util.TOMConfiguration;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.ObjectOutputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import org.apache.commons.codec.binary.Base64;
import threshsig.Dealer;
import threshsig.KeyShare;

/**
 *
 * @author 
 */
public class ThresholdSignatureGenerator {
    
    public ThresholdSignatureGenerator() {
        
   }
    
    public void run(int size) throws Exception {
        
        TOMConfiguration t = new TOMConfiguration(-1);
        
        // threshold parameters
        //int k = (t.isBFT() ? 2*t.getF() + 1 : t.getF() + 1);
        int k = (t.isBFT() ? t.getF() + 1 : 1);
        int l = t.getN();
        
        // Initialize a dealer with a keysize
        Dealer d = new Dealer(size);
        
        // Generate the set of key shares
        d.generateKeys(k, l);
        
        // save public key to disk
        RSAPublicKeySpec spec = new RSAPublicKeySpec(d.getGroupKey().getModulus(), d.getGroupKey().getExponent());
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PublicKey pub = factory.generatePublic(spec);
        savePubToFile(pub);
        
        //save shares to disk
        for (int i = 0; i < d.getShares().length; i++) {
            saveShareToFile(d.getShares()[i], i);
        }
    }
    
    private void savePubToFile(PublicKey puk) throws Exception {
        
            String path = "config"+System.getProperty("file.separator")+"keys"+
                System.getProperty("file.separator");
        
        BufferedWriter w = new BufferedWriter(new FileWriter(path+"thresholdkey",false));
        w.write(getKeyAsString(puk));
        w.flush();
        w.close();
        
    }
     private void saveShareToFile(KeyShare ks, int id) throws Exception {
        
            String path = "config"+System.getProperty("file.separator")+"keys"+
                System.getProperty("file.separator");
        
        ObjectOutputStream w = new ObjectOutputStream(new FileOutputStream(path+"share"+id,false));
        w.writeObject(ks);
        w.flush();
        w.close();
        
    }
     
    
    private String getKeyAsString(Key key) {
        byte[] keyBytes = key.getEncoded();

        return Base64.encodeBase64String(keyBytes);
    }
    
    public static void main(String[] args) throws Exception {
        try {
            new ThresholdSignatureGenerator().run(Integer.parseInt(args[0]));
        } catch(Exception e){
            System.err.println("Use: ThresholdSignatureGenerator <key size>");
        }
    }
}
