package com.securechat.securemessaging.security;

import javax.crypto.KeyAgreement;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class DHUtil {

    // Generate DH key pair
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator =
                KeyPairGenerator.getInstance("DH");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    // Convert public key to string
    public static String publicKeyToString(PublicKey key) {
        return Base64.getEncoder()
                .encodeToString(key.getEncoded());
    }

    // Convert string back to public key
    public static PublicKey stringToPublicKey(String keyStr)
            throws Exception {

        byte[] bytes =
                Base64.getDecoder().decode(keyStr);

        X509EncodedKeySpec spec =
                new X509EncodedKeySpec(bytes);

        KeyFactory factory =
                KeyFactory.getInstance("DH");

        return factory.generatePublic(spec);
    }

    // Generate shared secret
    public static byte[] generateSharedSecret(
            PrivateKey privateKey,
            PublicKey otherPublicKey) throws Exception {

        KeyAgreement agreement =
                KeyAgreement.getInstance("DH");

        agreement.init(privateKey);
        agreement.doPhase(otherPublicKey, true);

        return agreement.generateSecret();
    }
}
