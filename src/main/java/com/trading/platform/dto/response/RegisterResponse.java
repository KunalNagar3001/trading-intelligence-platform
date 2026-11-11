package com.trading.platform.dto.response;

/** What the register endpoint returns: never the password hash. */
public record RegisterResponse(Long id, String name, String email) {}
