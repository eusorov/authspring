package com.authspring.api.web;

import com.authspring.api.security.UserPrincipal;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Example secured REST endpoint for verifying JWT authentication. */
@RestController
@RequestMapping(path = "/api/secured", version = "1")
public class SecuredTestController {

    @GetMapping("/test")
    public Map<String, Object> secured(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of(
                "message", "secured-ok",
                "userId", principal.getId(),
                "email", principal.getUsername());
    }
}
