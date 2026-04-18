package com.authspring.api.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.authspring.api.config.VerificationProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class LaravelSignedUrlValidatorTest {

    private static final String KEY = "test-verification-signing-key-32chars!!";

    private final LaravelSignedUrlValidator validator =
            new LaravelSignedUrlValidator(new VerificationProperties(KEY, "https://example.com", 60));

    @Test
    void validSignatureAndExpiryAccepted() throws Exception {
        String fullUrl = "https://example.com/api/email/verify/1/deadbeef";
        String expires = "9999999999";
        String original = fullUrl + "?expires=" + expires;
        String sig = hmacSha256Hex(original, KEY);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setRequestURI("/api/email/verify/1/deadbeef");
        request.setQueryString("expires=" + expires + "&signature=" + sig);
        request.addParameter("expires", expires);
        request.addParameter("signature", sig);

        assertTrue(validator.hasValidSignature(request));
    }

    @Test
    void wrongSignatureRejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);
        request.setRequestURI("/api/email/verify/1/deadbeef");
        request.setQueryString("expires=9999999999&signature=wrong");
        request.addParameter("expires", "9999999999");
        request.addParameter("signature", "wrong");

        assertFalse(validator.hasValidSignature(request));
    }

    @Test
    void stripSignatureParameter_removesSignatureByName() {
        assertTrue(
                LaravelSignedUrlValidator.stripSignatureParameter("expires=1&signature=abc")
                        .equals("expires=1"));
    }

    private static String hmacSha256Hex(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(out);
    }
}
