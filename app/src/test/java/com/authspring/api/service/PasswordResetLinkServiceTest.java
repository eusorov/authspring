package com.authspring.api.service;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.authspring.api.domain.PasswordResetToken;
import com.authspring.api.domain.User;
import com.authspring.api.repo.PasswordResetTokenRepository;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetLinkServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetEmailSender passwordResetEmailSender;

    @InjectMocks
    private PasswordResetLinkService passwordResetLinkService;

    @Test
    void unknownEmail_returnsUserNotFound() throws Exception {
        when(userRepository.findByEmail("a@b.com")).thenReturn(Optional.empty());

        var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("A@B.COM"));

        assertInstanceOf(SendPasswordResetLinkOutcome.UserNotFound.class, outcome);
        verify(passwordResetTokenRepository, never()).save(any());
        verify(passwordResetEmailSender, never()).send(any(), any());
    }

    @Test
    void knownUser_savesHashedTokenAndSendsEmail() throws Exception {
        User user = new User("Ada", "ada@example.com", "hash", "user");
        when(userRepository.findByEmail("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("hashed-token");

        var outcome = passwordResetLinkService.send(new ForgotPasswordRequest("ada@example.com"));

        assertInstanceOf(SendPasswordResetLinkOutcome.Sent.class, outcome);
        ArgumentCaptor<String> plainCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordResetEmailSender).send(eq(user), plainCaptor.capture());
        String plain = plainCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(64, plain.length());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("ada@example.com", tokenCaptor.getValue().getEmail());
        org.junit.jupiter.api.Assertions.assertEquals("hashed-token", tokenCaptor.getValue().getToken());
    }
}
