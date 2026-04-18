package com.authspring.api.web;

import com.authspring.api.security.EmailVerifiedGuard;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@EmailVerifiedGuard
@RestController
@RequestMapping(path = "/api/needsverified", version = "1")
public class NeedsVerifiedController {

    @GetMapping
    public Map<String, String> needsVerified() {
        return Map.of("status", "needsverified-ok");
    }
}
