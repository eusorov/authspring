package com.authspring.api.service;

import com.authspring.api.domain.User;
import com.authspring.api.repo.UserRepository;
import com.authspring.api.security.JwtService;
import com.authspring.api.web.dto.RegisterRequest;
import com.authspring.api.web.dto.RegisterResponse;
import com.authspring.api.web.dto.UserResponse;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterService {

    public static final String SUCCESS_MESSAGE =
            "User registered successfully. Please check your email to verify your account.";

    private static final String DEFAULT_ROLE = "user";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PersonalAccessTokenService personalAccessTokenService;

    public RegisterService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PersonalAccessTokenService personalAccessTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.personalAccessTokenService = personalAccessTokenService;
    }

    @Transactional
    public RegistrationOutcome register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email).isPresent()) {
            return new RegistrationOutcome.EmailAlreadyTaken();
        }
        Instant now = Instant.now();
        User user = new User(
                request.name().trim(), email, passwordEncoder.encode(request.password()), DEFAULT_ROLE);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        String token = jwtService.createToken(user);
        personalAccessTokenService.recordLoginToken(user, token);
        RegisterResponse response =
                new RegisterResponse(SUCCESS_MESSAGE, token, UserResponse.fromEntity(user));
        return new RegistrationOutcome.Registered(response);
    }
}
