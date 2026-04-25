package com.savelogins;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for AES-256-GCM encryption and decryption.
 * Provides secure symmetric encryption for storing passwords.
 */
public class EncryptionUtil {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_SIZE = 256;

    private static SecretKey secretKey;
    private static boolean initialized = false;

    /**
     * Initializes the encryption key from the stored key file,
     * or generates a new one if none exists.
     *
     * @param keyData The Base64-encoded key data, or null to generate a new key
     */
    public static void initialize(String keyData) {
        if (keyData != null && !keyData.isEmpty()) {
            try {
                byte[] decodedKey = Base64.getDecoder().decode(keyData);
                secretKey = new SecretKeySpec(decodedKey, "AES");
                initialized = true;
            } catch (IllegalArgumentException e) {
                // Invalid key data, generate new one
                generateNewKey();
            }
        } else {
            generateNewKey();
        }
    }

    /**
     * Generates a new AES-256 key.
     */
    private static void generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            secretKey = keyGenerator.generateKey();
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate encryption key", e);
        }
    }

    /**
     * Returns the Base64-encoded encryption key for storage.
     *
     * @return The encoded key, or null if not initialized
     */
    public static String getEncodedKey() {
        if (secretKey == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    /**
     * Checks if the encryption utility is initialized.
     *
     * @return true if initialized
     */
    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Encrypts the given plaintext using AES-256-GCM.
     *
     * @param plaintext The text to encrypt
     * @return The Base64-encoded encrypted data (IV + ciphertext + auth tag)
     */
    public static String encrypt(String plaintext) {
        if (!initialized) {
            throw new IllegalStateException("EncryptionUtil not initialized");
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            // Combine IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            byteBuffer.put(iv);
            byteBuffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts the given encrypted data using AES-256-GCM.
     *
     * @param encryptedData The Base64-encoded encrypted data
     * @return The decrypted plaintext
     */
    public static String decrypt(String encryptedData) {
        if (!initialized) {
            throw new IllegalStateException("EncryptionUtil not initialized");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);

            // Extract IV and ciphertext
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] ciphertext = new byte[byteBuffer.remaining()];
            byteBuffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}