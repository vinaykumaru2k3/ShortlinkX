package com.link.shortlinkx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
        @NotBlank(message = "URL cannot be blank")
        @Size(max = 2048, message = "URL must be at most 2048 characters")
        @Pattern(regexp = "^https?://\\S+$", message = "Invalid URL format: only http and https URLs are allowed")
        String url
) {}
