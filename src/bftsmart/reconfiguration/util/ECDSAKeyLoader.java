/**
Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, Tulio A. Ribeiro, and the authors indicated in the @author tags

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
package bftsmart.reconfiguration.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.util.encoders.Hex;

import bftsmart.tom.util.KeyLoader;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class ECDSAKeyLoader implements KeyLoader {

	private String path;
	private int id;
	private PrivateKey priKey;
	
	private String sigAlgorithm;

	private boolean defaultKeys;

	private KeyPair keyPair = null;

	private static final String PRIV_KEY_521b = "-----BEGIN EC PRIVATE KEY-----\n" + 
			"MIHcAgEBBEIBzxjh6rdpJ3nkuPzuvfWRWV2MzUJu1Ke0H04oeLRwLRXf/O7dKeh8\n" + 
			"qAoYVFjk/1frbB+1NeXy9wqcRrFwA12hMOygBwYFK4EEACOhgYkDgYYABAGisA9t\n" + 
			"ro3qToc5S3tCWlfjyoFECT+rOHY90jNBTx/hEKkD1blSE1JI3Og+O/pYuFMvdimw\n" + 
			"Alyce0xGO70brMsoDAEfj/0s4hfwrFZYwkyhA0LocATpG0tuG+Y9bfVp+BqCQrZQ\n" + 
			"o6dT0wqyHCI/51UGLnw4QKsKV8YcFwU27yNqPYRnLg==\n" + 
			"-----END EC PRIVATE KEY-----";
	
	private static final String PRIV_KEY_384b = "-----BEGIN EC PRIVATE KEY-----\n" + 
			"MIGkAgEBBDA+dcBGMPWZBb8uqYgbcac+KMFginhas9XreiTpYbgPlzeINkCqxe+B\n" + 
			"bwAephwASzmgBwYFK4EEACKhZANiAAQKRCyK0HNGv2MJl6vUax++4d5nAPAnILtQ\n" + 
			"y9cOZAllLZoqvg6IrUAZeFeHPtSnQmJJikqSFzw98zImR9CJD6Lb72qHCWGlKET/\n" + 
			"hWcGp90OLHgwxtxJ6nxpMxPAYetA9NY=\n" + 
			"-----END EC PRIVATE KEY-----";	
	
	private static final String PRIV_KEY_256b = "-----BEGIN EC PRIVATE KEY-----\n" + 
			"MHcCAQEEICMpf2ULckdRXAccLQtnN+1utW0rGQpvcZLl1qnJgovooAoGCCqGSM49\n" + 
			"AwEHoUQDQgAEoVYDlQiWUl9tjULrYFkWgJTeV3LVmbw8YZ7KwhUvBFam4+wnF6hV\n" + 
			"ADxIKpHwDJK7CjdZvFTPQRfRzetNNLazWw==\n" + 
			"-----END EC PRIVATE KEY-----";
	
	/** Creates a new instance of ECDSAKeyLoader */
	public ECDSAKeyLoader(int id, String configHome, boolean defaultKeys, String sigAlgorithm) {

		this.id = id;
		this.defaultKeys = defaultKeys;
		this.sigAlgorithm = sigAlgorithm;

		if (configHome.equals("")) {
			path = "config" + System.getProperty("file.separator") + "keysECDSA" + System.getProperty("file.separator");
		} else {
			path = configHome + System.getProperty("file.separator") + "keysECDSA"
					+ System.getProperty("file.separator");
		}

		Security.addProvider(new BouncyCastleProvider());
		Reader rdr = new StringReader (PRIV_KEY_256b);
	    Object parsed = null;
		try {
			parsed = new org.bouncycastle.openssl.PEMParser(rdr).readObject();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			keyPair = new org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter().getKeyPair((org.bouncycastle.openssl.PEMKeyPair)parsed);
		} catch (PEMException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	    System.out.println("\n###############################################" 
				+ "\nKeyLoader parameters:" 
				+ "\n\t ID: " + this.id
				+ "\n\t DefaultKeys: " + this.defaultKeys 
				+ "\n\t Signature Algorithm: " + keyPair.getPrivate().getAlgorithm()
				+ "\n\t Private Key Format: " + keyPair.getPrivate().getFormat()
				+ "\n\t Public Key Format: " + keyPair.getPublic().getFormat()
				+ "\n" + "###############################################");

	}

	/**
	 * Loads the public key of some processes from configuration files
	 *
	 * @return the PublicKey loaded from config/keys/publickey<id>
	 * @throws Exception
	 *             problems reading or parsing the key
	 */
	public PublicKey loadPublicKey(int id)
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

		if (defaultKeys) {
			return this.keyPair.getPublic();
		}

		FileReader f = new FileReader(path + "publickey" + id);
		BufferedReader r = new BufferedReader(f);
		String tmp = "";
		String key = "";
		while ((tmp = r.readLine()) != null) {
			key = key + tmp;
		}
		f.close();
		r.close();
		PublicKey ret = getPublicKeyFromString(key);
		return ret;
	}

	public PublicKey loadPublicKey()
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

		if (defaultKeys) {
			return keyPair.getPublic();
		}

		FileReader f = new FileReader(path + "publickey" + this.id);
		BufferedReader r = new BufferedReader(f);
		String tmp = "";
		String key = "";
		while ((tmp = r.readLine()) != null) {
			key = key + tmp;
		}
		f.close();
		r.close();
		PublicKey ret = getPublicKeyFromString(key);
		return ret;
	}

	/**
	 * Loads the private key of this process
	 *
	 * @return the PrivateKey loaded from config/keys/publickey<conf.getProcessId()>
	 * @throws Exception
	 *             problems reading or parsing the key
	 */
	public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

		if (defaultKeys) {
			return keyPair.getPrivate();
		}

		if (priKey == null) {
			FileReader f = new FileReader(path + "privatekey" + this.id);
			BufferedReader r = new BufferedReader(f);
			String tmp = "";
			String key = "";
			while ((tmp = r.readLine()) != null) {
				key = key + tmp;
			}
			f.close();
			r.close();
			priKey = getPrivateKeyFromString(key);
		}
		return priKey;
	}

	// utility methods for going from string to public/private key
	private PrivateKey getPrivateKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
		return this.keyPair.getPrivate();
	}

	private PublicKey getPublicKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
		return this.keyPair.getPublic();
	}

	@Override
	public String getSignatureAlgorithm() {
		return this.sigAlgorithm;
	}

}
