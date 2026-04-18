package com.authspring.api.service;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.JwtService;
import com.authspring.api.security.EmailVerificationHashes;
import com.authspring.api.security.LaravelSignedUrlValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final LaravelSignedUrlValidator laravelSignedUrlValidator;
    private final JwtService jwtService;
    private final FrontendProperties frontendProperties;
    private final PersonalAccessTokenService personalAccessTokenService;

    public EmailVerificationService(
            UserRepository userRepository,
            LaravelSignedUrlValidator laravelSignedUrlValidator,
            JwtService jwtService,
            FrontendProperties frontendProperties,
            PersonalAccessTokenService personalAccessTokenService) {
        this.userRepository = userRepository;
        this.laravelSignedUrlValidator = laravelSignedUrlValidator;
        this.jwtService = jwtService;
        this.frontendProperties = frontendProperties;
        this.personalAccessTokenService = personalAccessTokenService;
    }

    @Transactional
    public EmailVerificationOutcome verify(HttpServletRequest request, Long id, String hash) {
        if (!laravelSignedUrlValidator.hasValidSignature(request)) {
            return new EmailVerificationOutcome.InvalidOrExpiredLink();
        }
        User user = userRepository.findById(id).orElse(null);
        if (user == null) {
            return new EmailVerificationOutcome.InvalidOrExpiredLink();
        }
        String expectedHash = EmailVerificationHashes.sha1Hex(user.getEmail());
        if (!constantTimeEquals(expectedHash, hash.toLowerCase(Locale.ROOT))) {
            return new EmailVerificationOutcome.InvalidOrExpiredLink();
        }
        if (user.getEmailVerifiedAt() == null) {
            Instant now = Instant.now();
            user.setEmailVerifiedAt(now);
            user.setUpdatedAt(now);
            userRepository.save(user);
        }
        String token = jwtService.createToken(user);
        personalAccessTokenService.recordLoginToken(user, token);
        String url = buildRedirectUrl(user, token);
        return new EmailVerificationOutcome.RedirectToFrontend(url);
    }

    private String buildRedirectUrl(User user, String token) {
        String base = frontendProperties.baseUrl().replaceAll("/$", "");
        return base
                + "/?email_verified=1&api_token="
                + urlEncode(token)
                + "&auto_login=1&user_id="
                + user.getId()
                + "&user_name="
                + urlEncode(user.getName());
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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
