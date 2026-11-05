package com.example.taskmgr.utill;

import android.os.Build;
import android.util.Base64;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    public static final int DEFAULT_ITERATIONS = 100_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private PasswordHasher() {}

    public static String newSaltBase64() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hashBase64(String password, String saltBase64, int iterations) {
        try {
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance(algo());
            byte[] hash = f.generateSecret(spec).getEncoded();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("PBKDF2 failure", e);
        }
    }

    public static boolean verify(String password, String saltBase64,
                                 int iterations, String expectedHashBase64) {
        if (password == null || saltBase64 == null || expectedHashBase64 == null) return false;
        String actual = hashBase64(password, saltBase64, iterations);
        return constantTimeEquals(actual, expectedHashBase64);
    }

    private static String algo() {
        return Build.VERSION.SDK_INT >= 26
                ? "PBKDF2WithHmacSHA256"
                : "PBKDF2WithHmacSHA1";
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        int diff = a.length() ^ b.length();
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}