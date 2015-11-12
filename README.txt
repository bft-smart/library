This branch contains a version of BFT-SMaRt that implements the RSA threshold cryptography scheme of Victor Shoup. It uses a modified version of the ThreshSig library (available in http://threshsig.sourceforge.net/, but we include our modified code in zip and jar files). It basically makes replicas send their replies signed with the key shares, which then can be verified by the developers (or a third party).

This code can be compiled and executed in the same way as normal BFT-SMaRt. To enable the threshold scheme to work, use the following paramater in 'config/system.config':

system.threshold = true

The keyshares for each replica must be included in 'config/keys'. The public key must also be available in this directory at the client.

To create a new set of keyshares and public key, use class 'ThresholdSignatureGenerator' as follows:

java -cp ./bin/*:./lib/* bftsmart.tom.util.ThresholdSignatureGenerator <key size>

Finally, here it is an example of verifying the reply against the key shares within Java code:



import bftsmart.tom.util.CertificatedReply;
import bftsmart.tom.util.TOMUtil;
import java.security.interfaces.RSAPublicKey;

.........

int id = Integer.parseInt(args[0]);
ServiceProxy proxy = new ServiceProxy(id);
byte[] reply = proxy.invokeOrdered(out.toByteArray());

CertificatedReply certificate = proxy.getCertificatedReply();
RSAPublicKey thresholdKey = proxy.getPublicThresholdKey();
                    
if (certificate != null) System.out.println("Reply certified by enough replicas: " + TOMUtil.verifyReply(certificate, proxy.getViewController(), thresholdKey));
