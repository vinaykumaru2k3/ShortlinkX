package com.link.shortlinkx.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Propagates the identity established by Spring Security to downstream services.
 * Authentication itself happens in the security filter chain; this filter never decides access.
 * Identity headers sent by the client are always discarded so they cannot be spoofed.
 */
@Component
public class AuthenticationFilter implements GlobalFilter, Ordered {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_NAME_HEADER = "X-User-Name";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof UsernamePasswordAuthenticationToken && auth.isAuthenticated())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(authentication -> chain.filter(withTrustedHeaders(exchange, authentication.orElse(null))));
    }

    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, Authentication authentication) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(USER_ID_HEADER);
            headers.remove(USER_NAME_HEADER);
            // The gateway is the edge: a client-supplied X-Forwarded-For must not reach the click analytics.
            headers.remove("X-Forwarded-For");
            if (authentication != null && authentication.getDetails() instanceof Long userId) {
                headers.set(USER_ID_HEADER, String.valueOf(userId));
                headers.set(USER_NAME_HEADER, String.valueOf(authentication.getPrincipal()));
            }
        }).build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
