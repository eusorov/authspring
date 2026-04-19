package com.authspring.api.web;

import com.authspring.api.service.PasswordResetLinkService;
import com.authspring.api.service.SendPasswordResetLinkOutcome;
import com.authspring.api.web.dto.ForgotPasswordRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class ForgotPasswordController {

    private final PasswordResetLinkService passwordResetLinkService;

    public ForgotPasswordController(PasswordResetLinkService passwordResetLinkService) {
        this.passwordResetLinkService = passwordResetLinkService;
    }

    @PostMapping(
            value = "/forgot-password",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<Object> store(@Valid @ModelAttribute ForgotPasswordRequest request) {
        return switch (passwordResetLinkService.send(request)) {
            case SendPasswordResetLinkOutcome.Sent() -> ResponseEntity.ok(
                    Map.of("status", "We have e-mailed your password reset link!"));
            case SendPasswordResetLinkOutcome.UserNotFound() -> ResponseEntity.status(HttpStatusCode.valueOf(422))
                    .body(userNotFoundProblem());
        };
    }

    private static ProblemDetail userNotFoundProblem() {
        String msg = "We can't find a user with that e-mail address.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), msg);
        pd.setTitle("Password reset failed");
        pd.setProperty("message", "The given data was invalid.");
        pd.setProperty("errors", Map.of("email", List.of(msg)));
        return pd;
    }
}
