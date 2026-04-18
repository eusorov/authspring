package com.authspring.api.service;

public sealed interface PasswordResetOutcome permits PasswordResetOutcome.Success, PasswordResetOutcome.UserNotFound,
        PasswordResetOutcome.InvalidToken {

    record Success() implements PasswordResetOutcome {}

    record UserNotFound() implements PasswordResetOutcome {}

    record InvalidToken() implements PasswordResetOutcome {}
}
