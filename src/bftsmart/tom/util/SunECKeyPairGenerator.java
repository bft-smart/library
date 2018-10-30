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

import sun.security.ec.SunEC;

/*
 * Utility class used to generate a key pair for some process id on
 * config/keys/publickey<id> and config/keys/privatekey<id>
 *
 */
public class SunECKeyPairGenerator {

	/** Creates a new instance of KeyPairGenerator */
	public SunECKeyPairGenerator() {
	}

	/**
	 * Generate the key pair for the process with id = <id> and put it on the files
	 * config/keys/publickey<id> and config/keys/privatekey<id>
	 *
	 * @param id
	 *            the id of the process to generate key
	 * @throws Exception
	 *             something goes wrong when writing the files
	 */
	public void run(int id, String namedCurve) throws Exception {

		Security.addProvider(new SunEC());

		KeyPairGenerator kpg;
		kpg = KeyPairGenerator.getInstance("EC", "SunEC");
		ECGenParameterSpec ecsp;
		/**
		 * rfc8422 specifies to use this named curves:
		 * secp256r1 (23), secp384r1 (24), secp521r1 (25)
		 * the former are deprecated(1..22). 
		 * See: https://tools.ietf.org/html/rfc8422#section-2
		 */
		ecsp = new ECGenParameterSpec(namedCurve);
		kpg.initialize(ecsp);

		KeyPair kp = kpg.genKeyPair();
		PrivateKey prk = kp.getPrivate();
		PublicKey puk = kp.getPublic();

		saveToFile(id, puk, prk);
		System.out.println("Keys generated: " + id);
	}

	private void saveToFile(int id, PublicKey puk, PrivateKey prk) throws Exception {
		String path = "config" + System.getProperty("file.separator") + "keysSunEC"
				+ System.getProperty("file.separator");

		BufferedWriter w = new BufferedWriter(new FileWriter(path + "publickey" + id, false));
		w.write(getKeyAsString(puk));
		w.flush();
		w.close();

		w = new BufferedWriter(new FileWriter(path + "privatekey" + id, false));
		w.write(getKeyAsString(prk));
		w.flush();
		w.close();
	}

	private String getKeyAsString(Key key) {
		byte[] keyBytes = key.getEncoded();
		return Base64.encodeBase64String(keyBytes);
	}

	public static void main(String[] args) {
		try {
			new SunECKeyPairGenerator().run(Integer.parseInt(args[0]),args[1]);
		} catch (Exception e) {
			System.err.println("Usage: SunECKeyPairGenerator <id> <CurveName: secp256r1|secp384r1|secp521r1 (recommended by rfc8422)> ");
			e.printStackTrace();
		}
	}

}
