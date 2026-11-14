package com.link.shortlinkx.gateway.filter;

import com.link.shortlinkx.tracing.TraceConstants;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationHeaderFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String correlationId = request.getHeaders().getFirst(TraceConstants.CORRELATION_HEADER);

        // Only accept well-formed ids from the client; anything else could be used to forge log lines.
        if (correlationId == null || !correlationId.matches("[A-Za-z0-9-]{1,64}")) {
            correlationId = UUID.randomUUID().toString();
        }

        // Propagate header to downstream services
        final String headerValue = correlationId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(h -> h.set(TraceConstants.CORRELATION_HEADER, headerValue))
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        // Put correlation ID in reactive context
        String finalCorrelationId = correlationId;
        return chain.filter(mutatedExchange)
                .contextWrite(context -> context.put(TraceConstants.CORRELATION_KEY, finalCorrelationId));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
