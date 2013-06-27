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
package bftsmart.reconfiguration.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.apache.commons.codec.binary.Base64;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class RSAKeyLoader {

	private String path;

	private PrivateKey priKey;
	
	/** Creates a new instance of RSAKeyLoader */
	public RSAKeyLoader(String configHome) {
		if (configHome.equals("")) {
			path = "config" + System.getProperty("file.separator") + "keys" +
					System.getProperty("file.separator");
		} else {
			path = configHome + System.getProperty("file.separator") + "keys" +
					System.getProperty("file.separator");
		}
	}

	/**
	 * Loads the public key of some processes from configuration files
	 *
	 * @return the PublicKey loaded from config/keys/publickey<id>
	 * @throws Exception problems reading or parsing the key
	 */
	public PublicKey loadPublicKey() throws Exception {
		BufferedReader r = new BufferedReader(new FileReader(path + "publickey"));
		String tmp = "";
		String key = "";
		while ((tmp = r.readLine()) != null) {
			key = key + tmp;
		}
		r.close();
		PublicKey ret = getPublicKeyFromString(key);
		return ret;
	}

	/**
	 * Loads the private key of this process
	 *
	 * @return the PrivateKey loaded from config/keys/publickey<conf.getProcessId()>
	 * @throws Exception problems reading or parsing the key
	 */
	public PrivateKey loadPrivateKey() throws Exception {
		if (priKey == null) {
			BufferedReader r = new BufferedReader(
					new FileReader(path + "privatekey"));
			String tmp = "";
			String key = "";
			while ((tmp = r.readLine()) != null) {
				key = key + tmp;
			}
			r.close();
			priKey = getPrivateKeyFromString(key);
		}
		return priKey;
	}

	//utility methods for going from string to public/private key
	private PrivateKey getPrivateKeyFromString(String key) throws Exception {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(key));
		PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
		return privateKey;
	}

	private PublicKey getPublicKeyFromString(String key) throws Exception {
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(key));
		PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
		return publicKey;
	}
}
