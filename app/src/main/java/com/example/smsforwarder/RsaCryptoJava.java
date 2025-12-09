package com.example.smsforwarder;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

import javax.crypto.Cipher;

/**
 * Utility wrapper for RSA encryption used by the API client.
 */
public final class RsaCryptoJava {
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private RsaCryptoJava() {
    }

    public static String encrypt(String plainText, PublicKey publicKey) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
}
