package com.authspring.api.security;

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
}
