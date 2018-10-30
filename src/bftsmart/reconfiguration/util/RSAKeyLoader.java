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
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.apache.commons.codec.binary.Base64;

/**
 * Used to load JCA public and private keys from conf/keys/publickey<id> and
 * conf/keys/privatekey<id>
 */
public class RSAKeyLoader implements KeyLoader {

    private String path;
    private int id;
    private PrivateKey priKey;

    private String sigAlgorithm;

    // Keys size 1024
   /* private static String DEFAULT_UKEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCwuoTWbSFDnVjohwdZftoAwv3oCxUPnUiiNNH9\n" +
        "\npXryEW8kSFRGVJ7zJCwxJnt3YZGnpPGxnC3hAI4XkG26hO7+TxkgaYmv5GbamL946uZISxv0aNX3\n" +
        "\nYbaOf//MC6F8tShFfCnpWlj68FYulM5dC2OOOHaUJfofQhmXfsaEWU251wIDAQAB";

    private static String DEFAULT_PKEY = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBALC6hNZtIUOdWOiHB1l+2gDC/egL\n" +
        "\nFQ+dSKI00f2levIRbyRIVEZUnvMkLDEme3dhkaek8bGcLeEAjheQbbqE7v5PGSBpia/kZtqYv3jq\n" +
        "\n5khLG/Ro1fdhto5//8wLoXy1KEV8KelaWPrwVi6Uzl0LY444dpQl+h9CGZd+xoRZTbnXAgMBAAEC\n" +
        "\ngYAJaUVdrd4RnbV4XIh1qZ2uYLPowX5ToIqXqLxuB3vunCMRCZEDVcpJJGn+DBCTIO0CwnPkg26m\n" +
        "\nBsOKWbSeNCoN5gOi5yd6Poe0D40ZmvHP1hMCQ9LYhwjLB3Aa+Cl5gYL074Qe/eJFqJaYjZApkeJU\n" +
        "\nAy1HkXhM5OBW9grrXxg6YQJBAPTIni5fG5f2SYutkR2pUydZP4haKVabRkZr8nSHuClGDE2HzbNQ\n" +
        "\njb17z5rRVxJCKMLb2HiPg7ZsUgGK/J1ri78CQQC405h45rL1mCIyZQCXcK/nQZTVi8UaaelKN/ub\n" +
        "\nLQKtTGenJao/zoL+m39i+gGRkHWiG6HNaGFdOkRJmeeH+rfpAkEAn0fwDjKbDP4ZC0fM1uU4k7Ey\n" +
        "\nczJgFdgCGY7ifMtXnZvUI5sL0fPH15W6GH7BzsK4LVvK92BDj6/aiOB80p6JlwJASjL4NSE4mwv2\n" +
        "\nPpD5ydI9a/OSEqDIAjCerWMIKWXKe1P/EMU4MeFwCVLXsx523r9F2kyJinLrE4g+veWBY7+tcQJB\n" +
        "\nAKCTm3tbbwLJnnPN46mAgrYb5+LFOmHyNtEDgjxEVrzpQaCChZici2YGY1jTBjb/De4jii8RXllA\n" +
        "\ntUhBEsqyXDA=";*/

    // Keys size 2048
    private static String DEFAULT_UKEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgM/J5FjE+CoKi6/KEIasoaBRl3/nDihvwVNhh"
    		+ "w5GdqJ0OfHufkUlCDSHp7og44+ojqqc/WBqL1y8ldff/Khcnx1Ovre8a6uCbCSlYwUVXEldWZkSh+8eczifl1yfUP/bYwoZVgyqSSPvyGnE/"
    		+ "ZJjRnW2dKYvsRMhWSrGWYhQ8xUgGkytz6XdLHavBm+E4oBPyB8WeqH2Ypuf2sbPJ8Li3JDKDJLGr2w4JpXAtwi4KTHbyrYOE8ztNE+Ke/+GI"
    		+ "g8l85XV1McOS4aSkuYNFt7V9sMM2gVG/DUXZM+4M/C58wxPK04UX2g1R74C7Gn/BFmx8EKZd955qbYjrFHQ5BMOjwIDAQAB";
    
    private static String DEFAULT_PKEY = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQCAz8nkWMT4KgqLr8oQhqyhoFGXf+cOK"
    		+ "G/BU2GHDkZ2onQ58e5+RSUINIenuiDjj6iOqpz9YGovXLyV19/8qFyfHU6+t7xrq4JsJKVjBRVcSV1ZmRKH7x5zOJ+XXJ9Q/9tjChlWDKpJ"
    		+ "I+/IacT9kmNGdbZ0pi+xEyFZKsZZiFDzFSAaTK3Ppd0sdq8Gb4TigE/IHxZ6ofZim5/axs8nwuLckMoMksavbDgmlcC3CLgpMdvKtg4TzO0"
    		+ "0T4p7/4YiDyXzldXUxw5LhpKS5g0W3tX2wwzaBUb8NRdkz7gz8LnzDE8rThRfaDVHvgLsaf8EWbHwQpl33nmptiOsUdDkEw6PAgMBAAECgg"
    		+ "EAJ82NajUP8Es1ZQKV4tNqpqrea30HTbWrCscUHOKJXYFHftxGQNhm4AkzXd3C6e5rPGJ2DklrpmT2lSrWuMDhlf09Zutpmq+tpnMQc++PM"
    		+ "SwuX1BQjJaUBzCZB/GD/UiCZzEosRFK0Tz8jCY6y8wfRxd6oML1e7cYl/ACyIm50dLhKD6AndHn1n+IqYxiTZWsQZpjmgVtxZJOFue21/Py"
    		+ "2sSuTYP9mP4xbN+DhOmIyup4/opXEfpVp7DfKWL/wPmgTZNaWFT6n/t1bhhUJc8CDXWEWUihs0oAj/IjgGBYdzQuReVPrfHn2DmCiSh5TYF"
    		+ "qrkPe55MLUiJzfsMpyuojiQKBgQDz90///bVz4saK5e3v5prZiuJ7IHf9v5mY6uWnMOBBy+PoLpuk7kCXHgkPCpXdgcYMBe9NHIKfSCt+bP"
    		+ "rROlDPXRpiwdq+yegEAC4g4zYBXOD8UAWQvaFmVMUt/ggaElnuzFhD/6uUvEKUDFad2YAOIEUrGn1Od1X4aCjb4hp7fQKBgQCHKlx6Fmxgb"
    		+ "w0ahL0mX18WGSZxp+Ut+7YYRAKbubdl7rbBGSLEs6jS3WFRDObHbvYlpCUTaKpYaheyyDzIK8ZCxrnZJOhauD3YarlpSUY0VAHSdbwrWtwA"
    		+ "ht+nntiyy30zbhJmw8+d+h1ibYHLwm+nBwLDfGorsz6V6ytQYN7X+wKBgQDcgaVSSJuQIeG4O3hjBHjjtY9dIIyz0lDfETj/c8LOVZ4qBq5"
    		+ "xVVMWA3TGnpe0PQ6nYVnYxbMeipmdTJ0rLV1K/+jQaEzxcwb9Trhiy1rNwogsJZvSJkPSfT43gssJ3Zphp1sEIvuPlNVMgRZs1+DRY8OA4R"
    		+ "FvMZeHzYVYsLWk9QKBgBzwpPw+8NV08YlMAnPE18cTe3e6SwedbU+kwCo3iVz5z4doqlkTXoJHhB6mdIMe7vUAQC/3qQFlNc3BscHqHXkOs"
    		+ "5wamuVRrWw47ZntZmqt0fYN30wpGBHEzv5EtIETsKriVm5KXpmkg8YfTDskVmOczKquaM0Sg8P1pkB/fTchAoGAZuyT35wV82CRA8d8GGQg"
    		+ "PBMtsHBcSQAeORALo33vRPgxZ4GluKT1myp/NruYjMFQXuvOgox3fAio8AwJpduRJYmetw3MypX3VZBCyziByG9ks2fbgGflyZ/fLyzsVAg"
    		+ "4V6ErqMLP+pC5VYjs2PskXdGcw02y1COmHk3XIKSu+qw=";
    
    

    private boolean defaultKeys;

    /** Creates a new instance of RSAKeyLoader */
    public RSAKeyLoader(int id, String configHome, boolean defaultKeys, String sigAlgorithm) {

            this.id = id;
            this.defaultKeys = defaultKeys;
            this.sigAlgorithm = sigAlgorithm;
            
            if (configHome.equals("")) {
                    path = "config" + System.getProperty("file.separator") + "keysRSA" +
                                    System.getProperty("file.separator");
            } else {
                    path = configHome + System.getProperty("file.separator") + "keysRSA" +
                                    System.getProperty("file.separator");
            }

    }
    
    /**
     * Loads the public key of some processes from configuration files
     *
     * @return the PublicKey loaded from config/keys/publickey<id>
     * @throws Exception problems reading or parsing the key
     */
    public PublicKey loadPublicKey(int id) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

            if (defaultKeys) {
                return getPublicKeyFromString(RSAKeyLoader.DEFAULT_UKEY);
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

    public PublicKey loadPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {

            if (defaultKeys) {                    
                return getPublicKeyFromString(RSAKeyLoader.DEFAULT_UKEY);
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
     * @throws Exception problems reading or parsing the key
     */
    public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

            if (defaultKeys) {
                return getPrivateKeyFromString(RSAKeyLoader.DEFAULT_PKEY);
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
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(key));
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
            return privateKey;
    }

    private PublicKey getPublicKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(key));
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);
            return publicKey;
    }

    @Override
    public String getSignatureAlgorithm() {
        
        return this.sigAlgorithm;
    }
}
