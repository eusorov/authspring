package com.authspring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Digests used in email verification URLs.
 *
 * <p>The path segment hash is <strong>not</strong> a password or a secret: it is a public identifier
 * derived from the email. Tamper protection comes from {@link LaravelSignedUrlValidator}
 * (HMAC-SHA256 over the full URL including {@code expires} and {@code signature}), not from hiding
 * the email digest.
 */
public final class EmailVerificationHashes {

    private static final HexFormat HEX = HexFormat.of();

    private EmailVerificationHashes() {}

    /** {@code SHA-256(email)} as lowercase hex, for {@code GET /api/email/verify/{id}/{hash}}. */
    public static String sha256Hex(String email) {
        Objects.requireNonNull(email, "email");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(email.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
