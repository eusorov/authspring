package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlSigner {

    private final VerificationProperties properties;

    public LaravelSignedUrlSigner(VerificationProperties properties) {
        this.properties = properties;
    }

    /**
     * Absolute URL for GET /api/email/verify/{id}/{hash}?expires=...&signature=...
     * that {@link LaravelSignedUrlValidator#hasValidSignature} accepts for the same host/path/query.
     */
    public String buildVerifyEmailUrl(long userId, String email) {
        String key = properties.signingKey();
        if (key == null || key.isEmpty()) {
            throw new IllegalStateException("app.verification.signing-key is not set");
        }
        String base = properties.publicBaseUrl().replaceAll("/$", "");
        String hash = EmailVerificationHashes.sha256Hex(email);
        long expires = Instant.now().getEpochSecond() + properties.expireMinutes() * 60L;
        String originalWithoutSig =
                base + "/api/email/verify/" + userId + "/" + hash + "?expires=" + expires;
        String sig = hmacSha256Hex(originalWithoutSig, key);
        return originalWithoutSig + "&signature=" + sig;
    }

    private static String hmacSha256Hex(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
