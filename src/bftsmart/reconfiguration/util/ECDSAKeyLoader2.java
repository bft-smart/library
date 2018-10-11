package bftsmart.reconfiguration.util;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bftsmart.tom.util.KeyLoader;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class ECDSAKeyLoader2 implements KeyLoader {

	private Logger logger = LoggerFactory.getLogger(this.getClass());
	private String path;
	private int id;
	private PrivateKey privateKey = null;
	private PublicKey publicKey = null;

	private String sigAlgorithm;

		
	/** Creates a new instance of ECDSAKeyLoader 
	 * @throws InvalidAlgorithmParameterException */
	public ECDSAKeyLoader2(int id, String configHome, boolean defaultKeys, String sigAlgorithm) throws InvalidAlgorithmParameterException {

		this.id = id;
		this.sigAlgorithm = sigAlgorithm;

		Security.addProvider(new BouncyCastleProvider());

		KeyPairGenerator kpg = null;
		 try {
			kpg = KeyPairGenerator.getInstance("EC","SunEC");
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 ECGenParameterSpec ecsp;
		 ecsp = new ECGenParameterSpec("secp256r1");
		 kpg.initialize(ecsp);

		 KeyPair kp = kpg.genKeyPair();
		 privateKey = kp.getPrivate();
		 publicKey = kp.getPublic();
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
		
		logger.debug("Loading my default PublicKey, id: {}", this.id);
		return publicKey;
	}

	public PublicKey loadPublicKey()
			throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

		logger.debug("Loading my default PublicKey, this.id: {}", this.id);
		return publicKey;
		
	}

	/**
	 * Loads the private key of this process
	 *
	 * @return the PrivateKey loaded from config/keys/publickey<conf.getProcessId()>
	 * @throws Exception
	 *             problems reading or parsing the key
	 */
	public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		logger.debug("Loading default PrivateKey, ID: {}", this.id);
		return privateKey;
	}

	@Override
	public String getSignatureAlgorithm() {
		return this.sigAlgorithm;
	}

}
