package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.security.UserPrincipal;
import com.authspring.api.service.EmailVerificationNotificationOutcome;
import com.authspring.api.service.EmailVerificationNotificationService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiresAuth
@RestController
@RequestMapping(path = "/api", version = "1")
public class EmailVerificationNotificationController {

    private final EmailVerificationNotificationService notificationService;

    public EmailVerificationNotificationController(EmailVerificationNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Laravel: {@code EmailVerificationNotificationController::store}, route {@code verification.send.api}.
     */
    @PostMapping(
            value = "/email/verification-notification",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Object> store(@AuthenticationPrincipal UserPrincipal principal) {
        return switch (notificationService.send(principal)) {
            case EmailVerificationNotificationOutcome.AlreadyVerified() ->
                    ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(
                                    Map.of(
                                            "status",
                                            "email-already-verified",
                                            "message",
                                            "Email address is already verified"));
            case EmailVerificationNotificationOutcome.Sent() ->
                    ResponseEntity.ok(Map.of("status", "verification-link-sent"));
        };
    }
}
