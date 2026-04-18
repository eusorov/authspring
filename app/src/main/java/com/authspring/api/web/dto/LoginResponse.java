package com.authspring.api.web.dto;

public record LoginResponse(String token, UserResponse user) {}
