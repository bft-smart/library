package bftsmart.reconfiguration.util;

/**
 * https://docs.oracle.com/javase/7/docs/technotes/guides/security/SunProviders.html
 */

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import sun.security.ec.SunEC;
import sun.security.rsa.SunRsaSign;

public class TestECDSASignature {

	static int iterations = 1000;
	static boolean showLog = false;
	static int n = 1;
	static int n2 = 3;

	static String str =  new String("0123456789012345678901234567890123456789012345678901234567890123456789");
	static String str2 = new String("0123456789012345678901234567890123456789012345678901234567890123456789");


	public static void main(String[] args) throws InvalidKeyException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException, InvalidAlgorithmParameterException {

		
		System.out.println("Iteration(s): " + iterations + ", Message size: "+str.getBytes().length+" Bytes");
		BFTSMaRt_CaseTest();
		
		System.out.println("\n\nBase Test, 1 iteration.");
		showLog=true;
		baseTest();
		
	}
	
	
	public static void BFTSMaRt_CaseTest() throws NoSuchAlgorithmException, 
										SignatureException,
										InvalidKeyException,
										NoSuchProviderException,
										InvalidAlgorithmParameterException  {
			
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
		PrivateKey priv = null;
		PublicKey pub = null;
		long cliInitAndSign=0;
		long rVerify=0;
		long rInitAndSign=0;
		long rVerify3times=0;
		
		ECDSAKeyLoader ecdsaKeyLoader = new ECDSAKeyLoader(1001, "", true, "SHA512withECDSA");
		try {
			priv = ecdsaKeyLoader.loadPrivateKey();
			pub = ecdsaKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}
		for (int i = 0; i < iterations; i++) {		
			long result[] = doSMaRt_Test(priv, pub, new org.bouncycastle.jce.provider.BouncyCastleProvider(), "SHA512withECDSA", showLog);
			cliInitAndSign += result[0];
			rVerify += result[1];
			rInitAndSign += result[2];
			rVerify3times += result[3];
		}
		System.out.println("\nProvider: BC, Algorithm: SHA512withECDSA");
		System.out.println("Client init and sign (1 time): " + (double) cliInitAndSign / iterations + " ms");
		System.out.println("Replica verify signature (1 time): " + (double) rVerify/ iterations+ " ms ");
		System.out.println("Replica init and sign (1 time): " + (double) rInitAndSign/ iterations+ " ms ");
		System.out.println("Replica verify signature (3 times): " + (double) rVerify3times/ iterations+ " ms");
		
		cliInitAndSign=0;
		rVerify=0;
		rInitAndSign=0;
		rVerify3times=0;

		
		
		SunECKeyLoader sunECKeyLoader = new SunECKeyLoader(1001, "", true, "SHA512withECDSA");
		try {
			priv = sunECKeyLoader.loadPrivateKey();
			pub = sunECKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}
		
		for (int i = 0; i < iterations; i++) {		
			long result[] = doSMaRt_Test(priv, pub, new SunEC(), "SHA512withECDSA", showLog);
			cliInitAndSign += result[0];
			rVerify += result[1];
			rInitAndSign += result[2];
			rVerify3times += result[3];
		}
		System.out.println("\nProvider: SunEC, Algorithm: SHA512withECDSA");
		System.out.println("Client init and sign (1 time): " + (double) cliInitAndSign / iterations + " ms");
		System.out.println("Replica verify signature (1 time): " + (double) rVerify/ iterations+ " ms");
		System.out.println("Replica init and sign (1 time): " + (double) rInitAndSign/ iterations+ " ms");
		System.out.println("Replica verify signature (3 times): " + (double) rVerify3times/ iterations+ " ms");
		cliInitAndSign=0;
		rVerify=0;
		rInitAndSign=0;
		rVerify3times=0;
		
		
		RSAKeyLoader rsaKeyLoader = new RSAKeyLoader(1001, "", true, "SHA512withRSA");
		try {
			priv = rsaKeyLoader.loadPrivateKey();
			pub = rsaKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}

		for (int i = 0; i < iterations; i++) {		
			long result[] = doSMaRt_Test(priv, pub, new SunRsaSign(), "SHA512withRSA", showLog);
			cliInitAndSign += result[0];
			rVerify += result[1];
			rInitAndSign += result[2];
			rVerify3times += result[3];
		}
		System.out.println("\nProvider: SunRsaSign, Algorithm: SHA512withRSA");
		System.out.println("Client init and sign (1 time): " + (double) cliInitAndSign / iterations + " ms");
		System.out.println("Replica verify signature (1 time): " + (double) rVerify/ iterations+ " ms");
		System.out.println("Replica init and sign (1 time): " + (double) rInitAndSign/ iterations+ " ms");
		System.out.println("Replica verify signature (3 times): " + (double) rVerify3times/ iterations+ " ms");
		
		
	}
	
	public static void baseTest() throws NoSuchAlgorithmException, 
										SignatureException, 
										InvalidKeyException, 
										NoSuchProviderException, 
										InvalidAlgorithmParameterException  {

		ECDSAKeyLoader ecdsaKeyLoader = new ECDSAKeyLoader(1001, "", true, "SHA512withECDSA");
		PrivateKey priv = null;
		PublicKey pub = null;
		try {
			priv = ecdsaKeyLoader.loadPrivateKey();
			pub = ecdsaKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}
		doTest(priv, pub, new org.bouncycastle.jce.provider.BouncyCastleProvider(), "SHA512withECDSA", showLog);
		
		SunECKeyLoader sunECKeyLoader = new SunECKeyLoader(1001, "", true, "SHA512withECDSA");
		try {
			priv = sunECKeyLoader.loadPrivateKey();
			pub = sunECKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}
		doTest(priv, pub, new SunEC(), "SHA512withECDSA", showLog);
		
		RSAKeyLoader rsaKeyLoader = new RSAKeyLoader(1001, "", true, "SHA512withRSA");
		try {
			priv = rsaKeyLoader.loadPrivateKey();
			pub = rsaKeyLoader.loadPublicKey();
		} catch (InvalidKeySpecException | IOException | CertificateException e) {
			e.printStackTrace();
		}
		doTest(priv, pub, new SunRsaSign(), "SHA512withRSA", showLog);

		
		
	}

	private static void doTest(PrivateKey priv, PublicKey pub, Provider provider, String signatureAlgorithm,
			boolean showLog) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {

		byte[] signature;
		Signature signEngine;
		long start, end;
		boolean verified = false;

		signEngine = Signature.getInstance(signatureAlgorithm);

		if (showLog) {
			System.out.println("\n");
			System.out.println("Provider: " + provider.getName() + ", Algorithm: " + signatureAlgorithm);
		}

		start = System.currentTimeMillis();
			signEngine.initSign(priv);
			signEngine.update(str.getBytes());
			signature = signEngine.sign();
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Init and sign: " + (end - start) + " ms");

		start = System.currentTimeMillis();
			signEngine.initVerify(pub);
			signEngine.update(str2.getBytes());
			verified = signEngine.verify(signature);
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Verify signature: " + (end - start) + " ms, " + "\t Result: " + verified);

	}

	/**
	 * The following algorithms are available in the SunEC provider: Engine
	 * Algorithm Name(s) AlgorithmParameters EC KeyAgreement ECDH KeyFactory EC
	 * KeyPairGenerator EC Signature NONEwithECDSA SHA1withECDSA SHA256withECDSA
	 * SHA384withECDSA SHA512withECDSA
	 * 
	 */

	private static void doTestFactory(String KeyPairGeneratorParameter, Provider provider, String signatureAlg,
			boolean showLog) throws NoSuchAlgorithmException, NoSuchProviderException,
			InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {

		byte[] signature;
		Signature sunECSigEngine;
		long start, end;
		boolean verified = false;

		KeyPairGenerator kpg;
		kpg = KeyPairGenerator.getInstance(KeyPairGeneratorParameter, provider);
		ECGenParameterSpec ecsp;
		ecsp = new ECGenParameterSpec("secp256k1");
		kpg.initialize(ecsp);

		KeyPair kpU = kpg.genKeyPair();
		PrivateKey privKeyU = kpU.getPrivate();
		PublicKey pubKeyU = kpU.getPublic();

		sunECSigEngine = Signature.getInstance(signatureAlg);

		if (showLog) {
			System.out.println("\n");
			System.out.println("Provider: " + provider.getName() + ", Algorithm: " + signatureAlg);
		}

		start = System.currentTimeMillis();
			sunECSigEngine.initSign(privKeyU);
			sunECSigEngine.update(str.getBytes());
			signature = sunECSigEngine.sign();
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Init and sign: " + (end - start) + " ms");

		start = System.currentTimeMillis();
			sunECSigEngine.initVerify(pubKeyU);
			sunECSigEngine.update(str2.getBytes());
			verified = sunECSigEngine.verify(signature);
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Verify signature: " + (end - start) + " ms, " + "\t Result: " + verified);

	}
	
	private static void doTestFactory_SMART(String KeyPairGeneratorParameter, Provider provider, String signatureAlg,
			boolean showLog) throws NoSuchAlgorithmException, NoSuchProviderException,
			InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {

	
		byte[] signature;
		Signature sunECSigEngine;
		long start, end;
		boolean verified = false;

		KeyPairGenerator kpg;
		kpg = KeyPairGenerator.getInstance(KeyPairGeneratorParameter, provider);
		ECGenParameterSpec ecsp;
		ecsp = new ECGenParameterSpec("secp256k1");
		kpg.initialize(ecsp);

		KeyPair kpU = kpg.genKeyPair();
		PrivateKey privKeyU = kpU.getPrivate();
		PublicKey pubKeyU = kpU.getPublic();

		sunECSigEngine = Signature.getInstance(signatureAlg);

		if (showLog) {
			System.out.println("\n");
			System.out.println("Provider: " + provider.getName() + ", Algorithm: " + signatureAlg);
		}

		
		start = System.currentTimeMillis();
			sunECSigEngine.initSign(privKeyU);
			sunECSigEngine.update(str.getBytes());
			signature = sunECSigEngine.sign();
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Client init and sign: " + (end - start) + " ms");

		start = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			sunECSigEngine.initVerify(pubKeyU);
			sunECSigEngine.update(str2.getBytes());
			verified = sunECSigEngine.verify(signature);
		}
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica verify signature "+n+" time(s): " + (end - start) + " ms, " + "\t Result: " + verified);

		//second phase

		start = System.currentTimeMillis();
			sunECSigEngine.initSign(privKeyU);
			sunECSigEngine.update(str.getBytes());
			signature = sunECSigEngine.sign();
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica init and sign "+n+" time(s): " + (end - start) + " ms");

		start = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n2; j++) {
				sunECSigEngine.initVerify(pubKeyU);
				sunECSigEngine.update(str2.getBytes());
				verified = sunECSigEngine.verify(signature);
			}
		}
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica verify signature "+n2+" times: " + (end - start) + " ms, " + "\t Result: " + verified);

		
		
	}

	private static long [] doSMaRt_Test(
								PrivateKey priv, 
								PublicKey pub, 
								Provider provider, 
								String signatureAlgorithm,
								boolean showLog) 
										throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {

		
		byte[] signature;
		Signature signEngine;
		long start, end;
		boolean verified = false;
		long [] result = new long [4];

		signEngine = Signature.getInstance(signatureAlgorithm);

		if (showLog) {
			System.out.println("\n");
			System.out.println("Provider: " + provider.getName() + ", Algorithm: " + signatureAlgorithm);
		}

		start = System.currentTimeMillis();
			signEngine.initSign(priv);
			signEngine.update(str.getBytes());
			signature = signEngine.sign();
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Client init and sign: " + (end - start) + " ms");
		result [0] = (end - start);

		start = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			signEngine.initVerify(pub);
			signEngine.update(str2.getBytes());
			verified = signEngine.verify(signature);			
		}
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica verify signature "+n+" time(s): " + (end - start) + " ms, " + "\t Result: " + verified);
		result [1] = (end - start);
		//Second phase

		start = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {	
			signEngine.initSign(priv);
			signEngine.update(str.getBytes());
			signature = signEngine.sign();
		}
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica init and sign "+n+" time(s): " + (end - start) + " ms");
		result [2] = (end - start);

		start = System.currentTimeMillis();
		for (int i = 0; i < n; i++) {
			for (int j = 0; j < n2; j++) {
				signEngine.initVerify(pub);
				signEngine.update(str2.getBytes());
				verified = signEngine.verify(signature);
			}
		}
		end = System.currentTimeMillis();
		if (showLog)
			System.out.println("\t Replica verify signature "+n2+" times: " + (end - start) + " ms, " + "\t Result: " + verified);
		result [3] = (end - start);
		
		return result;
	
	}

}