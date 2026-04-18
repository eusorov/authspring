package com.authspring.api.service;

public sealed interface EmailVerificationOutcome
        permits EmailVerificationOutcome.RedirectToFrontend, EmailVerificationOutcome.InvalidOrExpiredLink {

    record RedirectToFrontend(String redirectUrl) implements EmailVerificationOutcome {}

    record InvalidOrExpiredLink() implements EmailVerificationOutcome {}
}
