/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package navigators.smart.communication.server;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 *
 * @author Christian Spann <christian.spann at uni-ulm.de>
 */
public class HMacVerifier implements MessageVerifier<byte[]> {

    private static final Logger log = Logger.getLogger(HMacVerifier.class.getName());

    private static final String MAC_ALGORITHM = "HmacMD5";
    private static final String PASSWORD = "newcs";

    private SecretKey authKey;
    private Mac macSend;
    private Mac macReceive;
    private int macSize;


     public HMacVerifier(){
     }

      //TODO!
    public void authenticateAndEstablishAuthKey() {
        if (authKey != null) {
            return;
        }

        try {
            //if (conf.getProcessId() > remoteId) {
            // I asked for the connection, so I'm first on the auth protocol
            //DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
            //} else {
            // I received a connection request, so I'm second on the auth protocol
            //DataInputStream dis = new DataInputStream(socket.getInputStream());
            //}

            SecretKeyFactory fac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            PBEKeySpec spec = new PBEKeySpec(PASSWORD.toCharArray());
            authKey = fac.generateSecret(spec);

            macSend = Mac.getInstance(MAC_ALGORITHM);
            macSend.init(authKey);
            macReceive = Mac.getInstance(MAC_ALGORITHM);
            macReceive.init(authKey);
            macSize = macSend.getMacLength();
        } catch (InvalidKeySpecException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (InvalidKeyException ex) {
            log.log(Level.SEVERE, null, ex);
        } catch (NoSuchAlgorithmException ex) {
            log.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Generates the hash for the given message
     * @param messageData The data to hash
     * @return The generated hash
     */
    public byte[] generateHash(byte[] messageData) {
        return macSend.doFinal(messageData);
    }


    /**
     * Returns the size of the provided hashes
     * @return The hashsize
     */
    public int getHashSize() {
        return macSize;
    }

    /**
     * Verifies the given data with the given hash
     * @param data The data to check
     * @param receivedHash The provided hash to compare to
     * @return true if the hash fits the data, false otherwhise
     */
    @Override
    public byte[] verifyHash(byte[] data, byte[] receivedHash) {
        if( Arrays.equals(macReceive.doFinal(data), receivedHash)){
            return receivedHash;
        } else {
            return null;
        }
    }
}
