package com.authspring.api.service;

public sealed interface EmailVerificationNotificationOutcome {
    record AlreadyVerified() implements EmailVerificationNotificationOutcome {}

    record Sent() implements EmailVerificationNotificationOutcome {}
}
