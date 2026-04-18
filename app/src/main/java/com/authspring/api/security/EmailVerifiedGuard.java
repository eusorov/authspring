package com.authspring.api.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Requires authenticated {@link UserPrincipal} with verified email. Delegates to {@link EmailVerifiedGuardBean}
 * so a custom {@link org.springframework.security.access.AccessDeniedException} message can be mapped in {@code
 * GlobalExceptionHandler}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@emailVerifiedGuard.check(authentication)")
public @interface EmailVerifiedGuard {}
