package com.authspring.api.service;

import com.authspring.api.web.dto.RegisterResponse;

public sealed interface RegistrationOutcome permits RegistrationOutcome.Registered, RegistrationOutcome.EmailAlreadyTaken {

    record Registered(RegisterResponse response) implements RegistrationOutcome {}

    record EmailAlreadyTaken() implements RegistrationOutcome {}
}
