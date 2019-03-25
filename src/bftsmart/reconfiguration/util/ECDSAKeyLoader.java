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
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.tom.util.KeyLoader;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class ECDSAKeyLoader implements KeyLoader {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private String path;
	private int id;
	private PrivateKey privateKey = null;

	private String sigAlgorithm;

	private boolean defaultKeys;

	//Bouncy Castle | 256 key size
	private static final String PRIVATE_KEY = "MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQgMMpfKtHS5ZlgHDPj3TG41Y0t5r9NIzx7p4YPZxn5gBmgCgYIKoZIzj0DAQehRANCAAQkD2DTG37xnxtcMMLJMiUCyObUdVJE+rMM9WQ1Z3sjtIZchN8Xefr02Ag+giXGLej862qu3v4/fy6UGJNAHNx3";
	private static final String PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEJA9g0xt+8Z8bXDDCyTIlAsjm1HVSRPqzDPVkNWd7I7SGXITfF3n69NgIPoIlxi3o/Otqrt7+P38ulBiTQBzcdw==";

	//Bouncy Castle | 384 key size
	//private static final String PRIVATE_KEY = "MIG/AgEAMBAGByqGSM49AgEGBSuBBAAiBIGnMIGkAgEBBDAgJ7PlzZFc8/rsMTODSsQgenL2+WDxGjohmwGSLe8wp0+dYX8x2M2CidHdjzQu41qgBwYFK4EEACKhZANiAASFk9m7oKkuGqHg+BDMo51XbDnvIahxmfcagDgOvU/plgjpyuoY74eJit2LlLhLyKzVq0Rmpr1dZAuQWhlZfmlvdv0RhScaSaOiea54VdO8ZhdHzHHDwZQkJiSJQAuWOv0=";
	//private static final String PUBLIC_KEY = "MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEhZPZu6CpLhqh4PgQzKOdV2w57yGocZn3GoA4Dr1P6ZYI6crqGO+HiYrdi5S4S8is1atEZqa9XWQLkFoZWX5pb3b9EYUnGkmjonmueFXTvGYXR8xxw8GUJCYkiUALljr9";

	//Bouncy Castle | 521 key size
	//private static final String PRIVATE_KEY = "MIH3AgEAMBAGByqGSM49AgEGBSuBBAAjBIHfMIHcAgEBBEIBlz6dy43Dp2XHkJzP00oY4japCVdVjYqUZdmDJwTnNfPCmiBA362OO8XgHzkSoz11W/YhXN3NNyBZg0gWNC1E4ZmgBwYFK4EEACOhgYkDgYYABAH0rMKko/e9Wp8f0G01SCbQaRBkZ/9PvxBKG3GbFUFeR5TiCf1GH8UNLHn5q6+ayD9RfhtOuSj2JuLKzZwAFNo12QAPXa/COqKxdwzoLnUcc81i1I/NEsgVp4eqHjs4UPzP9mvWE+D+XqXAqEU8cK+CMA9IXvdIrUU/szSvkhWT5nw0EA==";
	//private static final String PUBLIC_KEY = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQB9KzCpKP3vVqfH9BtNUgm0GkQZGf/T78QShtxmxVBXkeU4gn9Rh/FDSx5+auvmsg/UX4bTrko9ibiys2cABTaNdkAD12vwjqisXcM6C51HHPNYtSPzRLIFaeHqh47OFD8z/Zr1hPg/l6lwKhFPHCvgjAPSF73SK1FP7M0r5IVk+Z8NBA=";

	/** Creates a new instance of ECDSAKeyLoader */

	public ECDSAKeyLoader(int id, String configHome, boolean defaultKeys, String sigAlgorithm) {
		this.id = id;
		this.defaultKeys = defaultKeys;
		this.sigAlgorithm = sigAlgorithm;

		if (configHome.equals("")) {
			path = "config" + System.getProperty("file.separator") + "keysECDSA" 
					+ System.getProperty("file.separator");
		} else {
			path = configHome + System.getProperty("file.separator") + "keysECDSA"
					+ System.getProperty("file.separator");
		}
		
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
			//logger.debug("Loading default PublicKey, id: {}", id);			
			try {
				return getPublicKeyFromString(PUBLIC_KEY);
			} catch (NoSuchProviderException e) {
				// TODO Auto-generated catch block
				logger.error("Provider error.",e);
			}
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
		PublicKey ret = null;
		logger.debug("Loading PublicKey from file, id: {}", id);
		try {
			ret = getPublicKeyFromString(key);
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			logger.error("Provider error.",e);
		}
		logger.trace("ID: {}, PublicKey Format: {}, PublicKey Algorithm: {} ",
				new Object[] { id, ret.getFormat(), ret.getAlgorithm() });
		return ret;
	}

	public PublicKey loadPublicKey()
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

		if (defaultKeys) {
			try {
				return getPublicKeyFromString(PUBLIC_KEY);
			} catch (NoSuchProviderException e) {
				logger.error("Provider error.",e);
			}
		}
		logger.debug("Loading PublicKey from file, this.id: {}", this.id);

		FileReader f = new FileReader(path + "publickey" + this.id);
		BufferedReader r = new BufferedReader(f);
		String tmp = "";
		String key = "";
		while ((tmp = r.readLine()) != null) {
			key = key + tmp;
		}
		f.close();
		r.close();
		PublicKey ret = null;
		try {
			ret = getPublicKeyFromString(PUBLIC_KEY);
		} catch (NoSuchProviderException e) {
			// TODO Auto-generated catch block
			logger.error("Provider error.",e);
		}
		logger.trace("ID: {}, PublicKey Format: {}, PublicKey Algorithm: {} ",
				new Object[] { this.id, ret.getFormat(), ret.getAlgorithm() });
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
			//logger.info("Loading private key...");
			try {
				return getPrivateKeyFromString(PRIVATE_KEY);
			} catch (NoSuchProviderException e) {
				// TODO Auto-generated catch block
				logger.error("Provider error.",e);
			}
		}

		if (privateKey == null) {
			FileReader f = new FileReader(path + "privatekey" + this.id);
			BufferedReader r = new BufferedReader(f);
			String tmp = "";
			String key = "";
			while ((tmp = r.readLine()) != null) {
				key = key + tmp;
			}
			f.close();
			r.close();
			logger.debug("Loading first time PrivateKey from file, this.id: {}, \nKey:{}", this.id, key);
			try {
				privateKey = getPrivateKeyFromString(key);
				logger.trace("PrivateKey loaded for this.id: {}, PrivateKey Format: {}, PrivateKey Algorithm: {} ",
						new Object[] { this.id, privateKey.getFormat(), privateKey.getAlgorithm() });
			} catch (NoSuchProviderException e) {
				logger.error("Provider error.",e);
			}
		}
		logger.trace("Returning previous stored PrivateKey from file, this.id: {}", this.id);
		return privateKey;
	}

	// utility methods for going from string to public/private key
	private PrivateKey getPrivateKeyFromString(String key)
			throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {

		KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(key));
		privateKey = keyFactory.generatePrivate(privateKeySpec);
		return privateKey;
	}

	private PublicKey getPublicKeyFromString(String key)
			throws NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException, NoSuchProviderException {
		KeyFactory keyFactory = KeyFactory.getInstance("ECDSA", "BC");
		X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(key));
		PublicKey publicKey = keyFactory.generatePublic(pubKeySpec);
		return publicKey;
	}

	@Override
	public String getSignatureAlgorithm() {
		return this.sigAlgorithm;
	}
	
}
