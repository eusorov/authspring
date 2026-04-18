package com.authspring;

import com.authspring.api.config.FrontendProperties;
import com.authspring.api.config.JwtProperties;
import com.authspring.api.config.PasswordResetMailProperties;
import com.authspring.api.config.VerificationMailProperties;
import com.authspring.api.config.VerificationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "com.authspring.api")
@EnableConfigurationProperties({
    JwtProperties.class,
    FrontendProperties.class,
    PasswordResetMailProperties.class,
    VerificationMailProperties.class,
    VerificationProperties.class
})
public class AuthspringApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthspringApplication.class, args);
    }
}
