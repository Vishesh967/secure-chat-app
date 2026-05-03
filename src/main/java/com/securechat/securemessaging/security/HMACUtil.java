package com.securechat.securemessaging.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class HMACUtil {

    private static final String HMAC_ALGO = "HmacSHA256";

    public static String generateHMAC(String data, String key)
            throws Exception {

        SecretKeySpec secretKey =
                new SecretKeySpec(
                        key.getBytes(),
                        HMAC_ALGO);

        Mac mac = Mac.getInstance(HMAC_ALGO);
        mac.init(secretKey);

        byte[] hmacBytes =
                mac.doFinal(data.getBytes());

        return Base64.getEncoder()
                .encodeToString(hmacBytes);
    }

    public static boolean verifyHMAC(
            String data,
            String key,
            String receivedHmac) throws Exception {

        String calculated =
                generateHMAC(data, key);

        return calculated.equals(receivedHmac);
    }
}
