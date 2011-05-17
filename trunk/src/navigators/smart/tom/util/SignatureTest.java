/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.tom.util;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 *
 * @author alysson
 */
public class SignatureTest {

    public static void main(String[] args) throws Exception {
        byte[] data = new byte[20];
        byte[] signature;
        Signature signEng;
        long start, end;

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(1024);
        KeyPair kp = kpg.genKeyPair();
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        signEng = Signature.getInstance("SHA1withRSA");

        for(int i=0; i<1000; i++) {
            signEng = Signature.getInstance("SHA1withRSA");
            signEng.initSign(privateKey);
        }
        start = System.currentTimeMillis();
        for(int i=0; i<1000; i++) {
            signEng = Signature.getInstance("SHA1withRSA");
            signEng.initSign(privateKey);
        }
        end = System.currentTimeMillis();
        System.out.println("1000 init sign: "+(end-start)+"ms");

        for(int i=0; i<1000; i++) {
            signEng.update(data);
            signature = signEng.sign();
        }
        start = System.currentTimeMillis();
        for(int i=0; i<1000; i++) {
            signEng.update(data);
            signature = signEng.sign();
        }
        end = System.currentTimeMillis();
        System.out.println("1000 sign: "+(end-start)+"ms");

        signEng.update(data);
        signature = signEng.sign();

        for(int i=0; i<1000; i++) {
            signEng = Signature.getInstance("SHA1withRSA");
            signEng.initVerify(publicKey);
        }
        start = System.currentTimeMillis();
        for(int i=0; i<1000; i++) {
            signEng = Signature.getInstance("SHA1withRSA");
            signEng.initVerify(publicKey);
        }
        end = System.currentTimeMillis();
        System.out.println("1000 init verify: "+(end-start)+"ms");

        for(int i=0; i<1000; i++) {
            signEng.update(data);
            signEng.verify(signature);
        }
        start = System.currentTimeMillis();
        for(int i=0; i<1000; i++) {
            signEng.update(data);
            signEng.verify(signature);
        }
        end = System.currentTimeMillis();
        System.out.println("1000 verify: "+(end-start)+"ms");
    }

}
