/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.tom.util;

import java.io.IOException;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;

/**
 * The KeyLoader interface is used internally by BFT-SMaRt to load signature keys from disk. Developers can use implementations of this
 * interface so that if their application uses key structures similar to the library, these keys can be shared by both the library and
 * the application to simplify key management.
 */
public interface KeyLoader {
    
    /**
     * Fetches the public key for the specified replica/client.
     * 
     * @param id ID of the respective replica/client.
     * @return The replica/client's public key.
     * 
     * @throws IOException If there was an error reading the key file.
     * @throws NoSuchAlgorithmException If the algorithm specified in the configuration does not exist.
     * @throws InvalidKeySpecException If the key specs are not properly set.
     * @throws CertificateException If there was an error loading the public key certificate.
     */
    public PublicKey loadPublicKey(int id) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException;

    /**
     * Fetches the public key for this replica/client.
     * 
     * @return The replica/client's public key.
     * 
     * @throws IOException If there was an error reading the key file.
     * @throws NoSuchAlgorithmException If the algorithm specified by the key does not exist.
     * @throws InvalidKeySpecException If the key specs are not properly set.
     * @throws CertificateException If there was an error loading the public key certificate.
     */
    public PublicKey loadPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException;
    
    /**
     * Fetches the private key for this replica/client.
     * 
     * @return The replica/client's public key.
     * 
     * @throws IOException If there was an error reading the key file.
     * @throws NoSuchAlgorithmException If the algorithm specified in the configuration does not exist.
     * @throws InvalidKeySpecException If the key specs are not properly set .
     */
    public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;
    
    /**
     * Get the signature algorithm specified by the key.
     * @return The signature algorithm specified by the key.
     */
    public String getSignatureAlgorithm();
    
}
