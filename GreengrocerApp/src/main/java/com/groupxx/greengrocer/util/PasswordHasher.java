package com.groupxx.greengrocer.util;

import com.groupxx.greengrocer.config.AppConfig;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordHasher() {}

    public static String newSaltB64() {
        byte[] salt = new byte[AppConfig.SALT_BYTES];
        RNG.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashB64(char[] password, String saltB64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            PBEKeySpec spec = new PBEKeySpec(
                    password,
                    salt,
                    AppConfig.PBKDF2_ITERATIONS,
                    256
            );
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] key = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(key);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed.", e);
        }
    }

    public static boolean verify(char[] password, String saltB64, String expectedHashB64) {
        if (saltB64 == null || expectedHashB64 == null) return false;
        byte[] expected = Base64.getDecoder().decode(expectedHashB64);
        byte[] actual = Base64.getDecoder().decode(hashB64(password, saltB64));
        return MessageDigest.isEqual(expected, actual);
    }
}
