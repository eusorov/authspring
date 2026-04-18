package com.authspring.api.web;

import com.authspring.api.security.RequiresAuth;
import com.authspring.api.service.PersonalAccessTokenService;
import com.authspring.api.service.SessionService;
import com.authspring.api.web.dto.LoginRequest;
import com.authspring.api.web.dto.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api", version = "1")
public class LoginController {

    private final SessionService sessionService;
    private final PersonalAccessTokenService personalAccessTokenService;

    public LoginController(SessionService sessionService, PersonalAccessTokenService personalAccessTokenService) {
        this.sessionService = sessionService;
        this.personalAccessTokenService = personalAccessTokenService;
    }

    /**
     * JSON body {@code email}, {@code password}. Matches Laravel {@code AuthenticatedSessionController::store}
     * semantics; 422 {@link ProblemDetail} with extension {@code errors} on failure.
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> loginJson(@Valid @RequestBody LoginRequest request) {
        return loginResult(request);
    }

    /**
     * Form fields {@code email}, {@code password} (Postman: Body → form-data, or x-www-form-urlencoded).
     */
    @PostMapping(
            value = "/login",
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<?> loginForm(@Valid @ModelAttribute LoginRequest request) {
        return loginResult(request);
    }

    private ResponseEntity<?> loginResult(LoginRequest request) {
        LoginResponse response = sessionService.login(request);
        if (response == null) {
            return ResponseEntity.status(HttpStatusCode.valueOf(422)).body(invalidLoginProblemDetail());
        }
        return ResponseEntity.ok(response);
    }

    private static ProblemDetail invalidLoginProblemDetail() {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(422), "The provided credentials are incorrect.");
        pd.setTitle("Invalid credentials");
        pd.setProperty("message", "The given data was invalid.");
        pd.setProperty("errors", Map.of("email", List.of("The provided credentials are incorrect.")));
        return pd;
    }

    /**
     * Matches Laravel {@code AuthenticatedSessionController::destroy}: Bearer token required;
     * revokes the matching {@code personal_access_tokens} row; clients should still discard the JWT.
     */
    @RequiresAuth
    @PostMapping("/logout")
    public Map<String, String> destroy(HttpServletRequest request) {
        personalAccessTokenService.revokeByJwtFromRequest(request);
        return Map.of("message", "Logged out");
    }
}
