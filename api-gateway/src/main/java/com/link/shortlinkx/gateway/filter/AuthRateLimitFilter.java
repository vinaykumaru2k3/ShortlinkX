package com.link.shortlinkx.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;

/**
 * Fixed-window, per-IP rate limit on login and registration to slow down credential stuffing.
 * It fails open if Redis is unavailable so an outage there cannot lock everyone out.
 * The address used is the TCP peer, so behind a load balancer configure it to preserve client IPs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class AuthRateLimitFilter implements WebFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ReactiveStringRedisTemplate redis;
    private final long maxRequestsPerWindow;

    public AuthRateLimitFilter(ReactiveStringRedisTemplate redis,
                               @Value("${app.rate-limit.auth-per-minute:10}") long maxRequestsPerWindow) {
        this.redis = redis;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (exchange.getRequest().getMethod() != HttpMethod.POST || !path.startsWith("/api/v1/auth/")) {
            return chain.filter(exchange);
        }

        String key = "ratelimit:auth:" + clientAddress(exchange);
        return redis.opsForValue().increment(key)
                .flatMap(count -> count == 1L
                        ? redis.expire(key, WINDOW).onErrorReturn(true).thenReturn(count)
                        : Mono.just(count))
                .onErrorReturn(0L)
                .flatMap(count -> {
                    if (count > maxRequestsPerWindow) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(WINDOW.toSeconds()));
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    private String clientAddress(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
    }
}
