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
 *
 * @author joao
 */
public interface KeyLoader {
    
    public PublicKey loadPublicKey(int id) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException;
    public PublicKey loadPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException, CertificateException;
    public PrivateKey loadPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;
    public String getSignatureAlgorithm();
    
}
