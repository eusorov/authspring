package com.authspring.api.security;

import com.authspring.api.config.VerificationProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class LaravelSignedUrlValidator {

    private final VerificationProperties properties;

    public LaravelSignedUrlValidator(VerificationProperties properties) {
        this.properties = properties;
    }

    public boolean hasValidSignature(HttpServletRequest request) {
        if (!hasCorrectSignature(request)) {
            return false;
        }
        return signatureHasNotExpired(request);
    }

    public boolean hasCorrectSignature(HttpServletRequest request) {
        String key = properties.signingKey();
        if (key == null || key.isEmpty()) {
            return false;
        }
        String fullUrl = request.getRequestURL().toString();
        String qs = request.getQueryString();
        String withoutSig = stripSignatureParameter(qs);
        String original =
                withoutSig == null || withoutSig.isEmpty() ? fullUrl : fullUrl + "?" + withoutSig;
        String expected = hmacSha256Hex(original, key);
        String provided = request.getParameter("signature");
        return provided != null && constantTimeEquals(expected, provided);
    }

    static String stripSignatureParameter(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String part : queryString.split("&")) {
            int eq = part.indexOf('=');
            String name = eq >= 0 ? part.substring(0, eq) : part;
            if ("signature".equals(name)) {
                continue;
            }
            parts.add(part);
        }
        return String.join("&", parts);
    }

    private static boolean signatureHasNotExpired(HttpServletRequest request) {
        String exp = request.getParameter("expires");
        if (exp == null || exp.isEmpty()) {
            return true;
        }
        try {
            long ts = Long.parseLong(exp);
            return Instant.now().getEpochSecond() <= ts;
        } catch (NumberFormatException _) {
            return false;
        }
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

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
