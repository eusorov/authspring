package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.authspring.api.config.VerificationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LaravelSignedUrlSignerTest {

    private static final String KEY = "test-verification-signing-key-32chars!!";

    private final LaravelSignedUrlSigner signer =
            new LaravelSignedUrlSigner(new VerificationProperties(KEY, "https://example.com", 60));
    private final LaravelSignedUrlValidator validator =
            new LaravelSignedUrlValidator(new VerificationProperties(KEY, "https://example.com", 60));

    @Test
    void builtUrlPassesValidator() {
        String url = signer.buildVerifyEmailUrl(1L, "ada@example.com");
        int q = url.indexOf('?');
        String path = url.substring("https://example.com".length(), q);
        String qs = url.substring(q + 1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setRequestURI(path);
        request.setQueryString(qs);
        for (String part : qs.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                request.addParameter(part.substring(0, eq), part.substring(eq + 1));
            }
        }

        assertTrue(validator.hasValidSignature(request));
    }

    @Test
    void buildVerifyEmailUrl_containsPathHashExpiresAndSignature() {
        String url = signer.buildVerifyEmailUrl(7L, "u@example.com");
        assertTrue(url.startsWith("https://example.com/api/email/verify/7/"));
        String afterUser = url.substring("https://example.com/api/email/verify/7/".length());
        int q = afterUser.indexOf('?');
        assertTrue(q > 0);
        String hash = afterUser.substring(0, q);
        assertTrue(hash.matches("[0-9a-f]{64}"), "hash segment must be 64-char lowercase hex");
        assertTrue(url.contains("expires="));
        assertTrue(url.contains("signature="));
    }

    @Test
    void buildVerifyEmailUrl_stripsTrailingSlashFromPublicBase() {
        var s =
                new LaravelSignedUrlSigner(
                        new VerificationProperties(KEY, "https://api.example.com/", 60));
        String url = s.buildVerifyEmailUrl(1L, "ada@example.com");
        assertTrue(url.startsWith("https://api.example.com/api/email/verify/"));
    }

    @Test
    void buildVerifyEmailUrl_missingSigningKey_throws() {
        var s = new LaravelSignedUrlSigner(new VerificationProperties("", "https://example.com", 60));
        assertThrows(IllegalStateException.class, () -> s.buildVerifyEmailUrl(1L, "a@b.com"));

        var s2 = new LaravelSignedUrlSigner(new VerificationProperties(null, "https://example.com", 60));
        assertThrows(IllegalStateException.class, () -> s2.buildVerifyEmailUrl(1L, "a@b.com"));
    }
}
