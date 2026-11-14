package com.link.shortlinkx.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AuthenticationFilterTest {

    private final AuthenticationFilter filter = new AuthenticationFilter();

    private MockServerWebExchange spoofedExchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/urls")
                .header("X-User-Id", "999")
                .header("X-User-Name", "admin")
                .header("X-Forwarded-For", "6.6.6.6")
                .build());
    }

    @Test
    void anonymousRequestsLoseAnyClientSuppliedIdentityHeaders() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };

        filter.filter(spoofedExchange(), chain).block();

        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-User-Name"));
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Forwarded-For"));
    }

    @Test
    void authenticatedRequestsGetTheVerifiedIdentityNotTheSpoofedOne() {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };

        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.authenticated("alice", null, java.util.List.of());
        auth.setDetails(7L);

        filter.filter(spoofedExchange(), chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth))
                .block();

        assertEquals("7", forwarded.get().getRequest().getHeaders().getFirst("X-User-Id"));
        assertEquals("alice", forwarded.get().getRequest().getHeaders().getFirst("X-User-Name"));
        assertEquals(1, forwarded.get().getRequest().getHeaders().get("X-User-Id").size());
    }
}
