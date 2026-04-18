package com.authspring.api.service;

public sealed interface SendPasswordResetLinkOutcome
        permits SendPasswordResetLinkOutcome.Sent, SendPasswordResetLinkOutcome.UserNotFound {

    record Sent() implements SendPasswordResetLinkOutcome {}

    record UserNotFound() implements SendPasswordResetLinkOutcome {}
}
