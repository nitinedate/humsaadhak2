package org.humsaadhak.shared.util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for sensitive columns (bank account numbers). Stored form: "v1:" + base64(iv || ciphertext+tag).
 * A fresh random IV per value means equal inputs never produce equal outputs. Values without the prefix are
 * returned unchanged so existing plain rows keep working until they are re-saved.
 */
public final class FieldEncryptor {
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12, TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public FieldEncryptor(byte[] key) {
        if (key == null || key.length != 32) throw new IllegalArgumentException("The field encryption key must be 32 bytes (base64 of 32 random bytes).");
        this.key = new SecretKeySpec(key, "AES");
    }

    public static FieldEncryptor fromBase64(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) throw new IllegalArgumentException("Set HUMSAADHAK_FIELD_KEY (openssl rand -base64 32).");
        return new FieldEncryptor(Base64.getDecoder().decode(base64Key.trim()));
    }

    public String encrypt(String plain) {
        if (plain == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt a protected field: wrong key or tampered data", e);
        }
    }
}
