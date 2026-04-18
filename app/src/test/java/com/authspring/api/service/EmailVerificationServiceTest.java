package com.authspring.api.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.EmailVerificationHashes;
import com.authspring.api.security.JwtService;
import com.authspring.api.security.LaravelSignedUrlValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LaravelSignedUrlValidator laravelSignedUrlValidator;

    @Mock
    private JwtService jwtService;

    @Mock
    private FrontendProperties frontendProperties;

    @Mock
    private PersonalAccessTokenService personalAccessTokenService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Ada Lovelace", "ada@example.com", "secret", "user");
        ReflectionTestUtils.setField(user, "id", 42L);
    }

    @Test
    void invalidSignature_returnsInvalidOrExpired() {
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(false);

        var outcome = emailVerificationService.verify(request, 42L, "anyhash");

        assertInstanceOf(EmailVerificationOutcome.InvalidOrExpiredLink.class, outcome);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void missingUser_returnsInvalidOrExpired() {
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(true);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        var outcome = emailVerificationService.verify(request, 99L, EmailVerificationHashes.sha256Hex("ada@example.com"));

        assertInstanceOf(EmailVerificationOutcome.InvalidOrExpiredLink.class, outcome);
    }

    @Test
    void wrongHash_returnsInvalidOrExpired() {
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        var outcome = emailVerificationService.verify(request, 42L, "0".repeat(64));

        assertInstanceOf(EmailVerificationOutcome.InvalidOrExpiredLink.class, outcome);
        verify(userRepository, never()).save(any());
        verify(jwtService, never()).createToken(any());
    }

    @Test
    void matchingHash_uppercasePathSegment_stillValidates() {
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jwtService.createToken(user)).thenReturn("jwt");
        when(frontendProperties.baseUrl()).thenReturn("http://app");
        String correctHex = EmailVerificationHashes.sha256Hex("ada@example.com");
        String upper = correctHex.toUpperCase();

        var outcome = emailVerificationService.verify(request, 42L, upper);

        assertInstanceOf(EmailVerificationOutcome.RedirectToFrontend.class, outcome);
        verify(personalAccessTokenService).recordLoginToken(user, "jwt");
    }

    @Test
    void valid_unverifiedUser_savesVerifiesIssuesTokenAndRedirects() {
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jwtService.createToken(user)).thenReturn("signed-jwt");
        when(frontendProperties.baseUrl()).thenReturn("http://frontend.test");

        String hash = EmailVerificationHashes.sha256Hex("ada@example.com");
        var outcome = emailVerificationService.verify(request, 42L, hash);

        assertInstanceOf(EmailVerificationOutcome.RedirectToFrontend.class, outcome);
        var redirect = (EmailVerificationOutcome.RedirectToFrontend) outcome;
        org.junit.jupiter.api.Assertions.assertTrue(redirect.redirectUrl().startsWith("http://frontend.test/?email_verified=1&api_token="));
        org.junit.jupiter.api.Assertions.assertTrue(redirect.redirectUrl().contains("user_id=42"));

        verify(userRepository).save(user);
        org.junit.jupiter.api.Assertions.assertNotNull(user.getEmailVerifiedAt());
        verify(jwtService).createToken(user);
        verify(personalAccessTokenService).recordLoginToken(user, "signed-jwt");
    }

    @Test
    void valid_alreadyVerified_skipsSaveStillIssuesToken() {
        user.setEmailVerifiedAt(Instant.parse("2020-01-01T00:00:00Z"));
        when(laravelSignedUrlValidator.hasValidSignature(request)).thenReturn(true);
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(jwtService.createToken(user)).thenReturn("signed-jwt");
        when(frontendProperties.baseUrl()).thenReturn("http://app");

        String hash = EmailVerificationHashes.sha256Hex("ada@example.com");
        var outcome = emailVerificationService.verify(request, 42L, hash);

        assertInstanceOf(EmailVerificationOutcome.RedirectToFrontend.class, outcome);
        verify(userRepository, never()).save(any());
        verify(personalAccessTokenService).recordLoginToken(eq(user), eq("signed-jwt"));
    }
}
