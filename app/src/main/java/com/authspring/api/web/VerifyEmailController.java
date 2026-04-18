package com.authspring.api.web;

import com.authspring.api.service.EmailVerificationOutcome;
import com.authspring.api.service.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class VerifyEmailController {

    private final EmailVerificationService emailVerificationService;

    public VerifyEmailController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @GetMapping("/email/verify/{id}/{hash}")
    public ResponseEntity<?> verify(
            HttpServletRequest request, @PathVariable Long id, @PathVariable String hash) {
        return switch (emailVerificationService.verify(request, id, hash)) {
            case EmailVerificationOutcome.RedirectToFrontend(var url) ->
                    ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url).build();
            case EmailVerificationOutcome.InvalidOrExpiredLink() ->
                    ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        };
    }
}
