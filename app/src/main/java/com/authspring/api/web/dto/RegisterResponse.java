package com.authspring.api.web.dto;

public record RegisterResponse(String message, String token, UserResponse user) {}
