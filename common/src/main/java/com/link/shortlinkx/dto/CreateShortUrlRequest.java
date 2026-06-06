package com.link.shortlinkx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateShortUrlRequest(
        @NotBlank(message = "URL cannot be blank")
        @Pattern(regexp = "^(https?|ftp)://.*$", message = "Invalid URL format")
        String url
) {}
