package com.link.shortlinkx.service;

import com.link.shortlinkx.dto.CreateShortUrlRequest;
import com.link.shortlinkx.dto.ShortUrlResponse;
import com.link.shortlinkx.entity.Url;
import com.link.shortlinkx.exception.ResourceNotFoundException;
import com.link.shortlinkx.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        
        when(urlRepository.existsByShortCode(anyString())).thenReturn(false);
        
        Url urlEntity = Url.builder()
                .originalUrl("https://www.google.com")
                .shortCode("abcxyz")
                .clickCount(0L)
                .userId(1L)
                .build();
        
        when(urlRepository.save(any(Url.class))).thenReturn(urlEntity);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        ShortUrlResponse response = urlService.shortenUrl(request, 1L);

        assertNotNull(response);
        assertEquals("http://localhost:8080/api/v1/abcxyz", response.shortUrl());
        assertEquals("https://www.google.com", response.originalUrl());
        assertEquals("abcxyz", response.shortCode());
        assertEquals(0L, response.clickCount());

        verify(urlRepository, times(1)).save(any(Url.class));
        verify(valueOperations, times(1)).set(startsWith("url:"), eq("https://www.google.com"), any(Duration.class));
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
    void testGetOriginalUrlAndRecordClick_NotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("url:abcxyz")).thenReturn(null);
        when(urlRepository.findByShortCode("abcxyz")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            urlService.getOriginalUrlAndRecordClick("abcxyz", "192.168.1.1", "Direct", "Mozilla");
        });
    }

    @Test
    void testGetUrlsByUserId() {
        Url url = Url.builder()
                .originalUrl("https://www.google.com")
                .shortCode("abcxyz")
                .clickCount(5L)
                .userId(1L)
                .build();
        when(urlRepository.findAllByUserId(1L)).thenReturn(Collections.singletonList(url));

        List<ShortUrlResponse> history = urlService.getUrlsByUserId(1L);

        assertEquals(1, history.size());
        assertEquals("http://localhost:8080/api/v1/abcxyz", history.get(0).shortUrl());
        assertEquals(5L, history.get(0).clickCount());
    }
}
