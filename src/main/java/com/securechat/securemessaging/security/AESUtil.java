package com.securechat.securemessaging.security;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-128 CBC encryption with a random IV prepended to the ciphertext.
 *
 * Format stored in DB:  Base64( IV[16 bytes] || ciphertext )
 *
 * The hardcoded key is intentional for this academic project.
 * In production, replace with per-session keys derived from DH exchange.
 */
public class AESUtil {

    private static final String ALGO      = "AES";
    private static final String TRANSFORM = "AES/CBC/PKCS5Padding";
    private static final int    IV_LEN    = 16;

    // 128-bit key — keep in sync with HMACUtil key usage
    private static final byte[] SECRET_BYTES = "1234567890123456".getBytes();

    private static SecretKey getKey() {
        return new SecretKeySpec(SECRET_BYTES, ALGO);
    }

    /**
     * Encrypts plaintext and returns Base64( IV || ciphertext ).
     */
    public static String encrypt(String plaintext) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), new IvParameterSpec(iv));

        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

        // Prepend IV to ciphertext
        byte[] combined = new byte[IV_LEN + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, IV_LEN);
        System.arraycopy(ciphertext, 0, combined, IV_LEN, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts Base64( IV || ciphertext ) and returns the original plaintext.
     */
    public static String decrypt(String encryptedData) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        byte[] iv         = new byte[IV_LEN];
        byte[] ciphertext = new byte[combined.length - IV_LEN];

        System.arraycopy(combined, 0, iv, 0, IV_LEN);
        System.arraycopy(combined, IV_LEN, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));

        return new String(cipher.doFinal(ciphertext), "UTF-8");
    }

    // ── Binary overloads (for image encryption) ───────────────

    /**
     * Encrypts raw bytes and returns Base64( IV || ciphertext ).
     */
    public static String encryptBytes(byte[] data) throws Exception {
        byte[] iv = new byte[IV_LEN];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.ENCRYPT_MODE, getKey(), new IvParameterSpec(iv));

        byte[] ciphertext = cipher.doFinal(data);

        byte[] combined = new byte[IV_LEN + ciphertext.length];
        System.arraycopy(iv, 0, combined, 0, IV_LEN);
        System.arraycopy(ciphertext, 0, combined, IV_LEN, ciphertext.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * Decrypts Base64( IV || ciphertext ) and returns the original raw bytes.
     */
    public static byte[] decryptBytes(String encryptedData) throws Exception {
        byte[] combined = Base64.getDecoder().decode(encryptedData);

        byte[] iv         = new byte[IV_LEN];
        byte[] ciphertext = new byte[combined.length - IV_LEN];

        System.arraycopy(combined, 0, iv, 0, IV_LEN);
        System.arraycopy(combined, IV_LEN, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(TRANSFORM);
        cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));

        return cipher.doFinal(ciphertext);
    }
}
