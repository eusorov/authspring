package com.authspring.api.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Invoked from {@link EmailVerifiedGuard} via {@code @PreAuthorize("@emailVerifiedGuard.check(authentication)")}.
 * Throws {@link AccessDeniedException} with a stable detail message when the user is logged in but email is not
 * verified so {@link com.authspring.api.web.GlobalExceptionHandler} can surface it in Problem JSON.
 */
@Component("emailVerifiedGuard")
public class EmailVerifiedGuardBean {

    public static final String DEFAULT_EMAIL_NOT_VERIFIED_MESSAGE = "You must verify your email";

    public boolean check(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal user)) {
            return false;
        }
        if (user.getEmailVerifiedAt() != null) {
            return true;
        }
        throw new AccessDeniedException(DEFAULT_EMAIL_NOT_VERIFIED_MESSAGE);
    }
}
