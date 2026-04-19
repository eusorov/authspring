package com.authspring.api.security;

import com.authspring.api.repo.UserRepository;
import com.authspring.api.service.PersonalAccessTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PersonalAccessTokenService personalAccessTokenService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository userRepository,
            PersonalAccessTokenService personalAccessTokenService) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.personalAccessTokenService = personalAccessTokenService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getServletPath();
        if (path.isEmpty()) {
            path = request.getRequestURI();
        }
        if ("/api/login".equals(path) && "POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = header.substring(BEARER_PREFIX.length()).trim();
        if (rawToken.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseAndValidate(rawToken);
            if (!personalAccessTokenService.existsForJwtCompact(rawToken)) {
                filterChain.doFilter(request, response);
                return;
            }
            long userId = Long.parseLong(claims.getSubject());
            userRepository
                    .findById(userId)
                    .ifPresent(user -> {
                        UserPrincipal principal = new UserPrincipal(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        } catch (Exception _) {
            // Invalid or expired token: leave context unauthenticated
        }

        filterChain.doFilter(request, response);
    }
}
