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
package bftsmart.tom.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Arrays;

import bftsmart.reconfiguration.util.Configuration;
import java.security.Security;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TOMUtil {

    //private static final int BENCHMARK_PERIOD = 10000;
    private static Logger logger = LoggerFactory.getLogger(TOMUtil.class);

    //some message types
    public static final int RR_REQUEST = 0;
    public static final int RR_REPLY = 1;
    public static final int RR_DELIVERED = 2;
    public static final int STOP = 3;
    public static final int STOPDATA = 4;
    public static final int SYNC = 5;
    public static final int SM_REQUEST = 6;
    public static final int SM_REPLY = 7;
    public static final int SM_ASK_INITIAL = 11;
    public static final int SM_REPLY_INITIAL = 12;

    public static final int TRIGGER_LC_LOCALLY = 8;
    public static final int TRIGGER_SM_LOCALLY = 9;
    
    private static int signatureSize = -1;
    private static boolean init = false;
        
    private static String hmacAlgorithm = Configuration.DEFAULT_HMAC;
    private static String secretAlgorithm = Configuration.DEFAULT_SECRETKEY;
    private static String sigAlgorithm = Configuration.DEFAULT_SIGNATURE;
    private static String hashAlgorithm = Configuration.DEFAULT_HASH;
    
    private static String hmacAlgorithmProvider = Configuration.DEFAULT_HMAC_PROVIDER;
    private static String secretAlgorithmProvider = Configuration.DEFAULT_SECRETKEY_PROVIDER;
    private static String sigAlgorithmProvider = Configuration.DEFAULT_SIGNATURE_PROVIDER;
    private static String hashAlgorithmProvider = Configuration.DEFAULT_HASH_PROVIDER;
    
    private static final int SALT_SEED = 509;
    private static final int SALT_BYTE_SIZE = 64; // 512 bits
    private static final int HASH_BYTE_SIZE = 64; // 512 bits
    private static final int PBE_ITERATIONS = 1000;  

    public static void init(String hmacAlgorithm, String secretAlgorithm, String sigAlgorithm, String hashAlgorithm,
            String hmacAlgorithmProvider, String secretAlgorithmProvider, String sigAlgorithmProvider, String hashAlgorithmProvider) {
     
        if (!TOMUtil.init) {
            
            TOMUtil.hmacAlgorithm = hmacAlgorithm;
            TOMUtil.sigAlgorithm = sigAlgorithm;
            TOMUtil.secretAlgorithm = secretAlgorithm;
            TOMUtil.hashAlgorithm = hashAlgorithm;
        
            TOMUtil.hmacAlgorithmProvider = hmacAlgorithmProvider;
            TOMUtil.sigAlgorithmProvider = sigAlgorithmProvider;
            TOMUtil.secretAlgorithmProvider = secretAlgorithmProvider;
            TOMUtil.hashAlgorithmProvider = hashAlgorithmProvider;
            
            TOMUtil.init = true;
        }
    }    
    
    //******* EDUARDO BEGIN **************//
    public static byte[] getBytes(Object o) {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ObjectOutputStream obOut = null;
        try {
            obOut = new ObjectOutputStream(bOut);
            obOut.writeObject(o);
            obOut.flush();
            bOut.flush();
            obOut.close();
            bOut.close();
        } catch (IOException ex) {
            logger.error("Failed to serialize object",ex);
            return null;
        }

        return bOut.toByteArray();
    }

    public static Object getObject(byte[] b) {
        if (b == null)
            return null;

        ByteArrayInputStream bInp = new ByteArrayInputStream(b);
        try {
            ObjectInputStream obInp = new ObjectInputStream(bInp);
            Object ret = obInp.readObject();
            obInp.close();
            bInp.close();
            return ret;
        } catch (Exception ex) {
            return null;
        }
    }
    //******* EDUARDO END **************//

    /**
     * Sign a message.
     *
     * @param key the private key to be used to generate the signature
     * @param message the message to be signed
     * @return the signature
     */
    public static byte[] signMessage(PrivateKey key, byte[] message) {

        byte[] result = null;
        try {
            
            Signature signatureEngine = getSigEngine();

            signatureEngine.initSign(key);

            signatureEngine.update(message);

            result = signatureEngine.sign();
        } catch (Exception e) {
            logger.error("Failed to sign message",e);
        }

        return result;
    }

    /**
     * Verify the signature of a message.
     *
     * @param key the public key to be used to verify the signature
     * @param message the signed message
     * @param signature the signature to be verified
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifySignature(PublicKey key, byte[] message, byte[] signature) {

        boolean result = false;
        
        try {
            Signature signatureEngine = getSigEngine();

            signatureEngine.initVerify(key);

            result = verifySignature(signatureEngine, message, signature);
        } catch (Exception e) {
            logger.error("Failed to verify signature",e);
        }

        return result;
    }

    /**
     * Verify the signature of a message.
     *
     * @param initializedSignatureEngine a signature engine already initialized
     *        for verification
     * @param message the signed message
     * @param signature the signature to be verified
     * @return true if the signature is valid, false otherwise
     */
    public static boolean verifySignature(Signature initializedSignatureEngine, byte[] message, byte[] signature) throws SignatureException {

        initializedSignatureEngine.update(message);
        return initializedSignatureEngine.verify(signature);
    }

    public static String byteArrayToString(byte[] b) {
        String s = "";
        for (int i = 0; i < b.length; i++) {
            s = s + b[i];
        }

        return s;
    }

    public static boolean equalsHash(byte[] h1, byte[] h2) {
        return Arrays.equals(h2, h2);
    }

    public static final byte[] computeHash(byte[] data) {
        
        byte[] result = null;
        
        try {
            MessageDigest md = getHashEngine();
            result = md.digest(data);
            
        } catch (NoSuchAlgorithmException e) {
            logger.error("Failed to compute hash",e);
        } // TODO: shouldn't it be SHA?
                
        return result;
    }
    
    public static Signature getSigEngine() throws NoSuchAlgorithmException {
        
        return Signature.getInstance(TOMUtil.sigAlgorithm, Security.getProvider(TOMUtil.sigAlgorithmProvider));
    }
    
    public static MessageDigest getHashEngine() throws NoSuchAlgorithmException {
        
        return MessageDigest.getInstance(TOMUtil.hashAlgorithm, Security.getProvider(TOMUtil.hashAlgorithmProvider));
    }
    
    public static SecretKeyFactory getSecretFactory() throws NoSuchAlgorithmException {
        
        return SecretKeyFactory.getInstance(TOMUtil.secretAlgorithm, Security.getProvider(TOMUtil.secretAlgorithmProvider));
    }
    
    public static Mac getMacFactory() throws NoSuchAlgorithmException {
        
        return Mac.getInstance(TOMUtil.hmacAlgorithm, Security.getProvider(TOMUtil.hmacAlgorithmProvider));
    }
    
    public static PBEKeySpec generateKeySpec(char[] password) throws NoSuchAlgorithmException {
        
        // generate salt
        Random random = new Random(SALT_SEED);
        byte salt[] = new byte[SALT_BYTE_SIZE];
        random.nextBytes(salt);

        return new PBEKeySpec(password, salt, PBE_ITERATIONS, HASH_BYTE_SIZE);
        
    }
}
