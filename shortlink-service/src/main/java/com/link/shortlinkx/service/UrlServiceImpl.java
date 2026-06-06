package com.link.shortlinkx.service;

import com.link.shortlinkx.dto.CreateShortUrlRequest;
import com.link.shortlinkx.dto.ShortUrlResponse;
import com.link.shortlinkx.entity.Url;
import com.link.shortlinkx.exception.ResourceNotFoundException;
import com.link.shortlinkx.repository.UrlRepository;
import com.link.shortlinkx.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;
    private final AnalyticsService analyticsService;

    @Value("${app.short-url-prefix:http://localhost:8080/api/v1/}")
    private String shortUrlPrefix;

    private static final String CACHE_KEY_PREFIX = "url:";

    @Override
    @Transactional
    public ShortUrlResponse shortenUrl(CreateShortUrlRequest request, Long userId) {
        String shortCode;
        int retries = 0;
        do {
            shortCode = Base62Encoder.generateRandomCode(6);
            retries++;
            if (retries > 5) {
                shortCode = Base62Encoder.generateRandomCode(8);
            }
        } while (urlRepository.existsByShortCode(shortCode));

        Url urlEntity = Url.builder()
                .originalUrl(request.url())
                .shortCode(shortCode)
                .clickCount(0L)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build();

        Url savedUrl = urlRepository.save(urlEntity);

        // Cache the shortened URL in Redis for fast redirections (TTL 7 days)
        redisTemplate.opsForValue().set(
                CACHE_KEY_PREFIX + shortCode,
                savedUrl.getOriginalUrl(),
                Duration.ofDays(7)
        );

        return mapToResponse(savedUrl);
    }

    @Override
    public String getOriginalUrlAndRecordClick(String shortCode, String ipAddress, String referrer, String userAgent) {
        // 1. Try reading original URL from Redis cache (Fast path)
        String originalUrl = redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + shortCode);

        if (originalUrl == null) {
            // Cache miss (Slow path) - load from Database
            Url url = urlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Short URL not found for code: " + shortCode));
            
            originalUrl = url.getOriginalUrl();

            // Cache it back to Redis
            redisTemplate.opsForValue().set(
                    CACHE_KEY_PREFIX + shortCode,
                    originalUrl,
                    Duration.ofDays(7)
            );
        }

        // 2. Offload statistics writing and click increment to background threads asynchronously
        analyticsService.recordClick(shortCode, ipAddress, referrer, userAgent);

        return originalUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortUrlResponse> getUrlsByUserId(Long userId) {
        List<Url> urls = urlRepository.findAllByUserId(userId);
        return urls.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ShortUrlResponse mapToResponse(Url url) {
        return new ShortUrlResponse(
                shortUrlPrefix + url.getShortCode(),
                url.getOriginalUrl(),
                url.getShortCode(),
                url.getClickCount()
        );
    }
}
