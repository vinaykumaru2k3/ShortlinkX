package com.link.shortlinkx.dto;

public record ShortUrlResponse(
        String shortUrl,
        String originalUrl,
        String shortCode,
        Long clickCount
) {}
