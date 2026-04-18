package com.authspring.api.service;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.LaravelSignedUrlSigner;
import com.authspring.api.security.UserPrincipal;
import jakarta.mail.MessagingException;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationNotificationService {

    private final UserRepository userRepository;
    private final LaravelSignedUrlSigner signedUrlSigner;
    private final EmailVerificationMailSender mailSender;

    public EmailVerificationNotificationService(
            UserRepository userRepository,
            LaravelSignedUrlSigner signedUrlSigner,
            EmailVerificationMailSender mailSender) {
        this.userRepository = userRepository;
        this.signedUrlSigner = signedUrlSigner;
        this.mailSender = mailSender;
    }

    @Transactional(readOnly = true)
    public EmailVerificationNotificationOutcome send(UserPrincipal principal) {
        User user =
                userRepository
                        .findById(principal.getId())
                        .orElseThrow(() -> new IllegalStateException("User not found: " + principal.getId()));
        if (user.getEmailVerifiedAt() != null) {
            return new EmailVerificationNotificationOutcome.AlreadyVerified();
        }
        String url = signedUrlSigner.buildVerifyEmailUrl(user.getId(), user.getEmail());
        try {
            mailSender.send(user, url);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        return new EmailVerificationNotificationOutcome.Sent();
    }
}
