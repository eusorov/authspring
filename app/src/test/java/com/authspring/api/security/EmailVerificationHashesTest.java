package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmailVerificationHashesTest {

    /** Known vector: SHA-256("ada@example.com") as lowercase hex. */
    private static final String ADA_EMAIL_SHA256_HEX =
            "b5fc85e55755f9e0d030a10ab4429b6b2944855f9a0d60077fe832becbc41d72";

    @Test
    void sha256Hex_matchesGoldenVector() {
        assertEquals(ADA_EMAIL_SHA256_HEX, EmailVerificationHashes.sha256Hex("ada@example.com"));
    }

    @Test
    void sha256Hex_returns64LowercaseHexChars() {
        String hex = EmailVerificationHashes.sha256Hex("any@example.com");
        assertEquals(64, hex.length());
        assertTrue(hex.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')));
    }

    @Test
    void sha256Hex_null_throws() {
        assertThrows(NullPointerException.class, () -> EmailVerificationHashes.sha256Hex(null));
    }
}
