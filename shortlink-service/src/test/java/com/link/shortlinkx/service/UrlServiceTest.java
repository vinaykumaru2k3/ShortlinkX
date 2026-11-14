package com.link.shortlinkx.service;

import com.link.shortlinkx.dto.CreateShortUrlRequest;
import com.link.shortlinkx.dto.ShortUrlResponse;
import com.link.shortlinkx.entity.Url;
import com.link.shortlinkx.exception.BadRequestException;
import com.link.shortlinkx.exception.ResourceNotFoundException;
import com.link.shortlinkx.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository urlRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AnalyticsService analyticsService;

    @InjectMocks
    private UrlServiceImpl urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "shortUrlPrefix", "http://localhost:8080/api/v1/");
    }

    @Test
    void testShortenUrl_Success() {
        CreateShortUrlRequest request = new CreateShortUrlRequest("https://www.google.com");

        Url urlEntity = Url.builder()
                .originalUrl("https://www.google.com")
                .shortCode("abcxyz")
                .clickCount(0L)
                .userId(1L)
                .build();

        when(urlRepository.saveAndFlush(any(Url.class))).thenReturn(urlEntity);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ShortUrlResponse response = urlService.shortenUrl(request, 1L);

        assertNotNull(response);
        assertEquals("http://localhost:8080/api/v1/abcxyz", response.shortUrl());
        assertEquals("https://www.google.com", response.originalUrl());
        assertEquals("abcxyz", response.shortCode());
        assertEquals(0L, response.clickCount());

        verify(urlRepository, times(1)).saveAndFlush(any(Url.class));
        verify(valueOperations, times(1)).set(startsWith("url:"), eq("https://www.google.com"), any(Duration.class));
    }

    @Test
    void shortenUrl_retriesWithAnotherCodeOnUniqueConstraintViolation() {
        Url saved = Url.builder().originalUrl("https://example.com").shortCode("zzzzzz").clickCount(0L).build();
        when(urlRepository.saveAndFlush(any(Url.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate short_code"))
                .thenThrow(new DataIntegrityViolationException("duplicate short_code"))
                .thenReturn(saved);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ShortUrlResponse response = urlService.shortenUrl(new CreateShortUrlRequest("https://example.com"), 1L);

        assertEquals("zzzzzz", response.shortCode());
        verify(urlRepository, times(3)).saveAndFlush(any(Url.class));
    }

    @Test
    void shortenUrl_escalatesToLongerCodesAndEventuallyGivesUp() {
        when(urlRepository.saveAndFlush(any(Url.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(IllegalStateException.class,
                () -> urlService.shortenUrl(new CreateShortUrlRequest("https://example.com"), 1L));

        ArgumentCaptor<Url> captor = ArgumentCaptor.forClass(Url.class);
        verify(urlRepository, times(10)).saveAndFlush(captor.capture());
        List<Url> attempts = captor.getAllValues();
        assertEquals(6, attempts.get(0).getShortCode().length());
        assertEquals(8, attempts.get(9).getShortCode().length());
    }

    @Test
    void shortenUrl_rejectsDangerousOrMalformedTargets() {
        for (String bad : List.of("javascript:alert(1)", "ftp://example.com/file", "http://", "https:///path",
                "data:text/html,<script>", "http://exa mple.com", "http://localhost:8080/api/v1/abc123")) {
            assertThrows(BadRequestException.class,
                    () -> urlService.shortenUrl(new CreateShortUrlRequest(bad), 1L), bad);
        }
        verifyNoInteractions(urlRepository);
    }

    @Test
    void shortenUrl_rejectsOverlongUrls() {
        String tooLong = "https://example.com/" + "a".repeat(2100);

        assertThrows(BadRequestException.class, () -> urlService.shortenUrl(new CreateShortUrlRequest(tooLong), 1L));
    }

    @Test
    void testGetOriginalUrlAndRecordClick_CacheHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abcxyz")).thenReturn("https://www.google.com");

        String originalUrl = urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");

        assertEquals("https://www.google.com", originalUrl);
        verify(analyticsService, times(1)).recordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");
        verifyNoInteractions(urlRepository);
    }

    @Test
    void testGetOriginalUrlAndRecordClick_CacheMiss() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abcxyz")).thenReturn(null);

        Url urlEntity = Url.builder()
                .originalUrl("https://www.google.com")
                .shortCode("abcxyz")
                .build();
        when(urlRepository.findByShortCode("abcxyz")).thenReturn(Optional.of(urlEntity));

        String originalUrl = urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");

        assertEquals("https://www.google.com", originalUrl);
        verify(analyticsService, times(1)).recordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");
        verify(urlRepository, times(1)).findByShortCode("abcxyz");
        verify(valueOperations, times(1)).set(eq("url:abcxyz"), eq("https://www.google.com"), any(Duration.class));
    }

    @Test
    void testGetOriginalUrlAndRecordClick_NotFound_isNegativelyCached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abcxyz")).thenReturn(null);
        when(urlRepository.findByShortCode("abcxyz")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla"));

        verify(valueOperations).set(eq("url:abcxyz"), anyString(), any(Duration.class));
        verifyNoInteractions(analyticsService);
    }

    @Test
    void knownMissingCodesAreAnsweredFromTheNegativeCacheWithoutTouchingTheDatabase() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abcxyz")).thenReturn("__not_found__");

        assertThrows(ResourceNotFoundException.class, () ->
                urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla"));

        verifyNoInteractions(urlRepository, analyticsService);
    }

    @Test
    void redirectStillWorksWhenRedisIsDown() {
        when(redisTemplate.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        Url urlEntity = Url.builder().originalUrl("https://www.google.com").shortCode("abcxyz").build();
        when(urlRepository.findByShortCode("abcxyz")).thenReturn(Optional.of(urlEntity));

        String originalUrl = urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");

        assertEquals("https://www.google.com", originalUrl);
        verify(analyticsService).recordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");
    }

    @Test
    void testGetUrlsByUserId() {
        Url url = Url.builder()
                .originalUrl("https://www.google.com")
                .shortCode("abcxyz")
                .clickCount(5L)
                .userId(1L)
                .build();
        when(urlRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(Collections.singletonList(url));

        List<ShortUrlResponse> history = urlService.getUrlsByUserId(1L, 0, 50);

        assertEquals(1, history.size());
        assertEquals("http://localhost:8080/api/v1/abcxyz", history.get(0).shortUrl());
        assertEquals(5L, history.get(0).clickCount());
    }

    @Test
    void historyPageSizeIsCappedAndNegativePagesAreClamped() {
        when(urlRepository.findByUserIdOrderByCreatedAtDescIdDesc(eq(1L), any(Pageable.class)))
                .thenReturn(List.of());

        urlService.getUrlsByUserId(1L, -3, 100000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(urlRepository).findByUserIdOrderByCreatedAtDescIdDesc(eq(1L), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(UrlServiceImpl.MAX_PAGE_SIZE, captor.getValue().getPageSize());
    }
}
