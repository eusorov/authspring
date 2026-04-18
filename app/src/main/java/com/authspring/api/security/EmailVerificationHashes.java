package com.authspring.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EmailVerificationHashes {

    private EmailVerificationHashes() {}

    /** Laravel path segment: sha1(email) as lowercase hex. */
    public static String sha1Hex(String email) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(email.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
