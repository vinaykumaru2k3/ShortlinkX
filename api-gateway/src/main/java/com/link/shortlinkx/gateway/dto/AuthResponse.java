package com.link.shortlinkx.gateway.dto;

public record AuthResponse(
        String token,
        String username,
        Long userId
) {}
