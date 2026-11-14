package com.link.shortlinkx.service;

import com.link.shortlinkx.dto.CreateShortUrlRequest;
import com.link.shortlinkx.dto.ShortUrlResponse;

import java.util.List;

public interface UrlService {

    ShortUrlResponse shortenUrl(CreateShortUrlRequest request, Long userId);

    String getOriginalUrlAndRecordClick(String shortCode, String ipAddress, String referrer, String userAgent);

    /** Newest first; {@code size} is capped by the implementation. */
    List<ShortUrlResponse> getUrlsByUserId(Long userId, int page, int size);
}
