package com.authspring.api.service;

import com.authspring.api.config.JwtProperties;
import com.authspring.api.domain.PersonalAccessToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PersonalAccessTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists issued API JWTs in {@code personal_access_tokens} (Sanctum-compatible shape): stores
 * {@code SHA-256(jwt)} (64 hex chars) in {@code token}, not the raw JWT.
 */
@Service
public class PersonalAccessTokenService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PersonalAccessTokenRepository repository;
    private final JwtProperties jwtProperties;
    /** Spring proxy for same-class {@link Transactional} calls (not {@code this}). */
    private final PersonalAccessTokenService self;

    public PersonalAccessTokenService(
            PersonalAccessTokenRepository repository,
            JwtProperties jwtProperties,
            @Lazy PersonalAccessTokenService self) {
        this.repository = repository;
        this.jwtProperties = jwtProperties;
        this.self = self;
    }

    @Transactional
    public void recordLoginToken(User user, String jwtCompact) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.expirationMs());
        PersonalAccessToken row = new PersonalAccessToken();
        row.setTokenableType(User.class.getName());
        row.setTokenableId(user.getId());
        row.setName("api");
        row.setToken(sha256Hex(jwtCompact));
        row.setAbilities("[\"*\"]");
        row.setExpiresAt(expiresAt);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        repository.save(row);
    }

    /**
     * Revokes the DB row for the current Bearer JWT (same {@link #sha256Hex} as on login). No-op if header
     * missing/empty or no matching row (e.g. token issued before persistence existed).
     */
    @Transactional
    public void revokeByJwtFromRequest(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return;
        }
        String raw = header.substring(BEARER_PREFIX.length()).trim();
        if (raw.isEmpty()) {
            return;
        }
        self.revokeByJwtCompact(raw);
    }

    @Transactional
    public void revokeByJwtCompact(String jwtCompact) {
        repository.deleteByToken(sha256Hex(jwtCompact));
    }

    @Transactional(readOnly = true)
    public boolean existsForJwtCompact(String jwtCompact) {
        return repository.findByToken(sha256Hex(jwtCompact)).isPresent();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
