package com.authspring.api.service;

import com.authspring.api.domain.PasswordResetToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetLinkService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetEmailSender passwordResetEmailSender;

    public PasswordResetLinkService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetEmailSender passwordResetEmailSender) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetEmailSender = passwordResetEmailSender;
    }

    @Transactional
    public SendPasswordResetLinkOutcome send(ForgotPasswordRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return new SendPasswordResetLinkOutcome.UserNotFound();
        }
        String plain = randomToken(64);
        String hash = passwordEncoder.encode(plain);
        passwordResetTokenRepository.save(new PasswordResetToken(email, hash, Instant.now()));
        try {
            passwordResetEmailSender.send(user, plain);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException("Failed to send password reset email", e);
        }
        return new SendPasswordResetLinkOutcome.Sent();
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
