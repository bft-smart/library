package bftsmart.reconfiguration.util;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;


public class TestECDSASignature {

    private void run() throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        String text = "Sample String which we want to sign";

        // create a key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        KeyPair keyPair = kpg.generateKeyPair();
        PrivateKey prv = keyPair.getPrivate();
        PublicKey pub = keyPair.getPublic();

       // System.out.println("Private Key: \n" + prv.toString());
       // System.out.println("Public Key: \n" + pub.toString());
        
       // System.out.println("KeyPar Priv: "+keyPair.getPrivate());
        
        Signature sig = Signature.getInstance("SHA512withECDSA");

        sig.initSign(prv);
        sig.update(text.getBytes(StandardCharsets.ISO_8859_1));
        
        byte[] signature = sig.sign();
        
        //String text2 = "Sample String which we want to verify";
        String text2 = "Sample String which we want to sign";

        sig.initVerify(pub);
        sig.update(text2.getBytes(StandardCharsets.ISO_8859_1));
        boolean verified = sig.verify(signature);
        System.err.println("\nVerified: " + verified);
    }

    
    public static void main(String[] args) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
    	/*TestECDSASignature app = new TestECDSASignature();
      	app.run();*/
      	
      	ECDSAKeyLoader ecdsaKeyLoader = new ECDSAKeyLoader(0, "", true, "SHA256withECDSA");
      	try {
			PublicKey pub = ecdsaKeyLoader.loadPublicKey();
			System.out.println(""+ pub.getFormat().toString());
		} catch (InvalidKeySpecException | CertificateException | IOException e) {
			e.printStackTrace();
		}
			
      	
      	
      	
    }

}