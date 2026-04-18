package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.password-reset")
public record PasswordResetMailProperties(
        String subject,
        String greetingTemplate,
        List<String> lines,
        String actionLabel,
        List<String> footerLines,
        String salutation,
        int expiryMinutes,
        String fromAddress,
        String fromName) {}
