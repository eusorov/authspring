package com.authspring.api.service;

import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.web.dto.ResetPasswordRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String REMEMBER_ALPHANUM =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public PasswordResetOutcome reset(ResetPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return new PasswordResetOutcome.UserNotFound();
        }
        var tokenRow = passwordResetTokenRepository.findById(email);
        if (tokenRow.isEmpty() || !passwordEncoder.matches(request.token(), tokenRow.get().getToken())) {
            return new PasswordResetOutcome.InvalidToken();
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRememberToken(randomRememberToken());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        passwordResetTokenRepository.deleteById(email);
        return new PasswordResetOutcome.Success();
    }

    private static String randomRememberToken() {
        StringBuilder sb = new StringBuilder(60);
        for (int i = 0; i < 60; i++) {
            sb.append(REMEMBER_ALPHANUM.charAt(SECURE_RANDOM.nextInt(REMEMBER_ALPHANUM.length())));
        }
        return sb.toString();
    }
}
