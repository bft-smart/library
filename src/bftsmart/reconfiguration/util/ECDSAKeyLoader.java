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
package bftsmart.reconfiguration.util;

import bftsmart.tom.util.KeyLoader;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.binary.Base64;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class ECDSAKeyLoader implements KeyLoader {

    private String path;
    private int id;
    private PrivateKey priKey;

    private String sigAlgorithm;
    
    private Map<Integer, PublicKey> pubKeys;
    
    //generated with domain parameter prime256v1
    //private static String DEFAULT_PKEY = "MIGTAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBHkwdwIBAQQgTnHcXa7szRTx5k6r2oNuG4aAFs1UyVEbsI9H3tLVSqigCgYIKoZIzj0DAQehRANCAATe0MF+aL4zTbQvwM5ipCaTSNN1kBxOxvgMj+VCTXL+6BCoUOWwT67/ECMj5s7jiTiQC/bac2edbDqKPkLYn76Y";
    //private static String DEFAULT_UKEY ="MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3tDBfmi+M020L8DOYqQmk0jTdZAcTsb4DI/lQk1y/ugQqFDlsE+u/xAjI+bO44k4kAv22nNnnWw6ij5C2J++mA==";
    
    //generated with domain parameter secp256r1
    //private static String DEFAULT_PKEY = "MEECAQAwEwYHKoZIzj0CAQYIKoZIzj0DAQcEJzAlAgEBBCAd3DWH1lE9twy7K8HVX9uF/+tmAzgl567vfS67fz05vg==";
    //private static String DEFAULT_UKEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2i5bI5qktDHFMBNZ4v2XMGeOJreB+KO2I5R8ozGEs1DMKxkF0yLpqcNOptjDjsGFRBKPm3Fs/TfgTTlmG8obkQ==";

    //generated with domain parameter secp256k1
    private static String DEFAULT_PKEY = "MD4CAQAwEAYHKoZIzj0CAQYFK4EEAAoEJzAlAgEBBCBnhIob4JXH+WpaNiL72BlbtUMAIBQoM852d+tKFBb7fg==";
    private static String DEFAULT_UKEY = "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEavNEKGRcmB7u49alxowlwCi1s24ANOpOQ9UiFBxgqnO/RfOl3BJm0qE2IJgCnvL7XUetwj5C/8MnMWi9ux2aeQ==";
    
    //generated with domain parameter secp521r1
    //private static String DEFAULT_PKEY = "MGACAQAwEAYHKoZIzj0CAQYFK4EEACMESTBHAgEBBEIBvFkcBAE7qEo04W4mlfDLjbuII0SgEJB8o5dm7lWimgCH/KxFsosp+8eimfC2mxyV+ojjhtlg+v8dWVuDlRBedko=";
    //private static String DEFAULT_UKEY = "MIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQBJ6HADdASTTSAid8x1tUx9GIkruQX9IUBc24GJTmgAdEh4Gr9gkI+qr4ViyaRY9JsZ9tno9l/Dl07PjxGjw+EU1oAswrOmwNm6s4A+hyTixHZwRktQ+xmpUHXg0EQklvJDi161e5Ai7iX6pTPq1ySCxAtx/GdV78mBKpeEhyV2UUy3x8=";
    
    //generated with domain parameter sect571r1
    //private static String DEFAULT_PKEY = "MGYCAQAwEAYHKoZIzj0CAQYFK4EEACcETzBNAgEBBEgBZZp6QK35NNOQpJG/lXSNNzaurf5Mz97bfV3OK39E7GLYqKXX8W3AjcFMg4W6+z94qwBcf5rxg+ETfznw6fUWDqeazwZLqGU=";
    //private static String DEFAULT_UKEY = "MIGnMBAGByqGSM49AgEGBSuBBAAnA4GSAAQDoITHkD7zotSVc8XARYQ4N/HAPMf4hCMqwCjXyWR9QedWv/kL8oJiyVfx4vyBMFNuUXNacKxUNJ/gugfHdcId389sd/9b7dgHQzpe3uaKPH4vMTozdIaPlZ4JaDBqrRmCKkRViZ955TvLlseKqD3QDK2/CpyTeQ3G9gFm83LZf/xw/lc2mNO1AxItdtn1EMQ=";
    
    private boolean defaultKeys;

    /** Creates a new instance of RSAKeyLoader */
    public ECDSAKeyLoader(int id, String configHome, boolean defaultKeys, String sigAlgorithm) {

            this.id = id;
            this.defaultKeys = defaultKeys;
            this.sigAlgorithm = sigAlgorithm;
            
            if (configHome.equals("")) {
                    path = "config" + System.getProperty("file.separator") + "ecdsakeys" +
                                    System.getProperty("file.separator");
            } else {
                    path = configHome + System.getProperty("file.separator") + "ecdsakeys" +
                                    System.getProperty("file.separator");
            }
            
            pubKeys = new HashMap<>();
    }

    /**
     * Loads the public key of some processes from configuration files
     *
     * @return the PublicKey loaded from config/keys/publickey<id>
     * @throws Exception problems reading or parsing the key
     */
    public PublicKey loadPublicKey(int id) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

            if (defaultKeys) {
                return getPublicKeyFromString(ECDSAKeyLoader.DEFAULT_UKEY);
            }
            
            PublicKey ret = pubKeys.get(id);
            
            if (ret == null) {
                
                FileReader f = new FileReader(path + "publickey" + id);
                BufferedReader r = new BufferedReader(f);
                String tmp = "";
                String key = "";
                while ((tmp = r.readLine()) != null) {
                        key = key + tmp;
                }
                f.close();
                r.close();
                ret = getPublicKeyFromString(key);
                
                pubKeys.put(id, ret);
            }
            
            return ret;
    }

    public PublicKey loadPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

            if (defaultKeys) {                    
                return getPublicKeyFromString(ECDSAKeyLoader.DEFAULT_UKEY);
            }
            
            PublicKey ret = pubKeys.get(this.id);

            if (ret == null) {
                                
                FileReader f = new FileReader(path + "publickey" + this.id);
                BufferedReader r = new BufferedReader(f);
                String tmp = "";
                String key = "";
                while ((tmp = r.readLine()) != null) {
                        key = key + tmp;
                }
                f.close();
                r.close();
                ret = getPublicKeyFromString(key);
                
                pubKeys.put(this.id, ret);
            }
            
            return ret;
    }

    /**
     * Loads the private key of this process
     *
     * @return the PrivateKey loaded from config/keys/publickey<conf.getProcessId()>
     * @throws Exception problems reading or parsing the key
     */
    public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

            if (defaultKeys) {
                return getPrivateKeyFromString(ECDSAKeyLoader.DEFAULT_PKEY);
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

    //utility methods for going from string to public/private key
    private PrivateKey getPrivateKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(key));
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            return privateKey;
    }

    private PublicKey getPublicKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
            /*CertificateFactory kf = CertificateFactory.getInstance("X.509");
            InputStream certstream = new ByteArrayInputStream (Base64.decodeBase64(key));
            
            return kf.generateCertificate(certstream).getPublicKey();*/
            
            KeyFactory keyFactory = KeyFactory.getInstance("EC");

            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(key));
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            
            return publicKey;
    }

    @Override
    public String getSignatureAlgorithm() {
        
        return this.sigAlgorithm;
    }
}
