package com.link.shortlinkx.service;

import com.link.shortlinkx.dto.CreateShortUrlRequest;
import com.link.shortlinkx.dto.ShortUrlResponse;
import com.link.shortlinkx.entity.Url;
import com.link.shortlinkx.exception.BadRequestException;
import com.link.shortlinkx.exception.ResourceNotFoundException;
import com.link.shortlinkx.repository.UrlRepository;
import com.link.shortlinkx.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlServiceImpl implements UrlService {

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;
    private final AnalyticsService analyticsService;

    @Value("${app.short-url-prefix:http://localhost:8080/api/v1/}")
    private String shortUrlPrefix;

    private static final String CACHE_KEY_PREFIX = "url:";
    /** Cached for unknown codes so scans for random codes do not hit the database every time. */
    private static final String NOT_FOUND_MARKER = "__not_found__";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final Duration NOT_FOUND_TTL = Duration.ofMinutes(1);

    private static final int MAX_RETRIES = 10;
    private static final int MAX_URL_LENGTH = 2048;
    static final int MAX_PAGE_SIZE = 100;

    /**
     * Deliberately not transactional: a unique-constraint violation aborts a PostgreSQL transaction,
     * so each attempt must run in its own transaction (saveAndFlush) to be retryable. The unique
     * constraint on short_code is the source of truth, which also closes the check-then-insert race.
     */
    @Override
    public ShortUrlResponse shortenUrl(CreateShortUrlRequest request, Long userId) {
        String originalUrl = validateTargetUrl(request.url());

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            // Escalate to 8-char codes after 5 collisions on 6-char codes
            int length = attempt < 5 ? 6 : 8;
            Url candidate = Url.builder()
                    .originalUrl(originalUrl)
                    .shortCode(Base62Encoder.generateRandomCode(length))
                    .clickCount(0L)
                    .userId(userId)
                    .createdAt(LocalDateTime.now())
                    .build();
            try {
                Url saved = urlRepository.saveAndFlush(candidate);
                cachePut(saved.getShortCode(), saved.getOriginalUrl(), CACHE_TTL);
                return mapToResponse(saved);
            } catch (DataIntegrityViolationException collision) {
                log.debug("Short code collision on attempt {}, retrying", attempt + 1);
            }
        }
        throw new IllegalStateException("Failed to generate a unique short code after " + MAX_RETRIES + " attempts");
    }

    @Override
    public String getOriginalUrlAndRecordClick(String shortCode, String ipAddress, String referrer, String userAgent) {
        String originalUrl = cacheGet(shortCode);

        if (NOT_FOUND_MARKER.equals(originalUrl)) {
            throw notFound(shortCode);
        }

        if (originalUrl == null) {
            // Cache miss (slow path) - load from the database
            var found = urlRepository.findByShortCode(shortCode);
            if (found.isEmpty()) {
                cachePut(shortCode, NOT_FOUND_MARKER, NOT_FOUND_TTL);
                throw notFound(shortCode);
            }
            originalUrl = found.get().getOriginalUrl();
            cachePut(shortCode, originalUrl, CACHE_TTL);
        }

        // Offload statistics writing and click increment to a background thread
        analyticsService.recordClick(shortCode, ipAddress, referrer, userAgent);

        return originalUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortUrlResponse> getUrlsByUserId(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return urlRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(safePage, safeSize))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private String validateTargetUrl(String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty() || url.length() > MAX_URL_LENGTH) {
            throw new BadRequestException("URL must be between 1 and " + MAX_URL_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new BadRequestException("Invalid URL format");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new BadRequestException("Only http and https URLs can be shortened");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new BadRequestException("URL must contain a host");
        }
        if (pointsAtThisService(uri)) {
            throw new BadRequestException("Short links cannot point at the shortener itself");
        }
        return url;
    }

    /** Prevents redirect loops: a short link whose target is another short link of this service. */
    private boolean pointsAtThisService(URI target) {
        try {
            URI own = URI.create(shortUrlPrefix);
            return own.getHost() != null
                    && own.getHost().equalsIgnoreCase(target.getHost())
                    && effectivePort(own) == effectivePort(target)
                    && target.getPath() != null
                    && target.getPath().startsWith(own.getPath() == null ? "/" : own.getPath());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private ResourceNotFoundException notFound(String shortCode) {
        return new ResourceNotFoundException("Short URL not found for code: " + shortCode);
    }

    // Redis is only a cache: if it is down we degrade to the database instead of failing the request.
    private String cacheGet(String shortCode) {
        try {
            return redisTemplate.opsForValue().get(CACHE_KEY_PREFIX + shortCode);
        } catch (RuntimeException e) {
            log.warn("Redis read failed, falling back to the database: {}", e.toString());
            return null;
        }
    }

    private void cachePut(String shortCode, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY_PREFIX + shortCode, value, ttl);
        } catch (RuntimeException e) {
            log.warn("Redis write failed, continuing without caching: {}", e.toString());
        }
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
