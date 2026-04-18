package com.authspring.api.service;

import com.authspring.api.domain.User;

public sealed interface EmailVerificationOutcome
        permits EmailVerificationOutcome.RedirectToFrontend, EmailVerificationOutcome.InvalidOrExpiredLink {

    record RedirectToFrontend(User user, String redirectUrl) implements EmailVerificationOutcome {}

    record InvalidOrExpiredLink() implements EmailVerificationOutcome {}
}
