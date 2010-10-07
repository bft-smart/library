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
package navigators.smart.reconfiguration.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Hashtable;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class RSAKeyLoader {

    private String path;

    //private PublicKey[] pubKeys;
    private PrivateKey priKey;
    private TOMConfiguration conf;
    private Hashtable<Integer, PublicKey> pubKeys = new Hashtable<Integer, PublicKey>();

    /** Creates a new instance of RSAKeyLoader */
    public RSAKeyLoader(TOMConfiguration conf, String configHome) {

        this.conf = conf;



        if (configHome.equals("")) {

            path = "config" + System.getProperty("file.separator") + "keys" +
                    System.getProperty("file.separator");

        } else {

            path = configHome + System.getProperty("file.separator") + "keys" +
                    System.getProperty("file.separator");

        }

    }

    /**
     * Load the public keys from processes 0..conf.getN()-1 (all servers).
     *
     * @return the array of public keys loaded
     * @throws Exception problems reading or parsing the keys
     */

    /* public PublicKey[] loadServersPublicKeys() throws Exception {
    if(pubKeys == null){
    pubKeys = new PublicKey[conf.getN()];
    for(int i = 0; i < pubKeys.length; i++){
    pubKeys[i] = loadPublicKey(i);
    }
    }
    return pubKeys;
    }*/
    /**
     * Loads the public key of some processes from configuration files
     *
     * @param id the id of the process that we want to load the public key
     * @return the PublicKey loaded from config/keys/publickey<id>
     * @throws Exception problems reading or parsing the key
     */
    public PublicKey loadPublicKey(int id) throws Exception {

        PublicKey ret = this.pubKeys.get(id);

        if (ret == null) {


            BufferedReader r = new BufferedReader(new FileReader(path + "publickey" + id));

            String tmp = "";

            String key = "";

            while ((tmp = r.readLine()) != null) {

                key = key + tmp;

            }

            r.close();

            ret = getPublicKeyFromString(key);
            this.pubKeys.put(id, ret);
        }
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
                    new FileReader(path + "privatekey" + conf.getProcessId()));

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

        sun.misc.BASE64Decoder b64 = new sun.misc.BASE64Decoder();

        EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(b64.decodeBuffer(key));

        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return privateKey;

    }

    private PublicKey getPublicKeyFromString(String key) throws Exception {

        sun.misc.BASE64Decoder b64 = new sun.misc.BASE64Decoder();

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64.decodeBuffer(key));

        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        return publicKey;

    }
}

