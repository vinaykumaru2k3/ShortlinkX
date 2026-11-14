package com.link.shortlinkx.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AuthRateLimitFilterTest {

    private final ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(redis, 5);

    private MockServerWebExchange post(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    @Test
    void blocksLoginOnceTheWindowLimitIsExceeded() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.just(6L));
        AtomicBoolean reachedChain = new AtomicBoolean();
        WebFilterChain chain = exchange -> {
            reachedChain.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = post("/api/v1/auth/login");
        filter.filter(exchange, chain).block();

        assertFalse(reachedChain.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        assertNotNull(exchange.getResponse().getHeaders().getFirst("Retry-After"));
    }

    @Test
    void allowsRequestsUnderTheLimitAndStartsTheWindowOnTheFirstOne() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.just(1L));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        AtomicBoolean reachedChain = new AtomicBoolean();

        filter.filter(post("/api/v1/auth/login"), exchange -> {
            reachedChain.set(true);
            return Mono.empty();
        }).block();

        assertTrue(reachedChain.get());
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        AtomicBoolean reachedChain = new AtomicBoolean();

        filter.filter(post("/api/v1/auth/login"), exchange -> {
            reachedChain.set(true);
            return Mono.empty();
        }).block();

        assertTrue(reachedChain.get());
    }

    @Test
    void ignoresNonAuthPaths() {
        AtomicBoolean reachedChain = new AtomicBoolean();

        filter.filter(post("/api/v1/shorten"), exchange -> {
            reachedChain.set(true);
            return Mono.empty();
        }).block();

        assertTrue(reachedChain.get());
    }
}
