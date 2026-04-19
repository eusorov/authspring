package com.authspring.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail.verification")
public record VerificationMailProperties(
        String subject,
        String greetingTemplate,
        List<String> lines,
        String actionLabel,
        List<String> footerLines,
        String salutation,
        int expiryMinutes,
        String fromAddress,
        String fromName) {

    public VerificationMailProperties {
        lines = lines == null ? List.of() : List.copyOf(lines);
        footerLines = footerLines == null ? List.of() : List.copyOf(footerLines);
    }
}
