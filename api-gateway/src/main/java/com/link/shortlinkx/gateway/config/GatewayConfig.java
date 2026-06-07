package com.link.shortlinkx.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Value("${SHORTLINK_SERVICE_URI:http://localhost:8081}")
    private String shortlinkServiceUri;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("shortlink-service-shorten", r -> r.path("/api/v1/shorten")
                        .filters(f -> f.circuitBreaker(c -> c.setName("shortlinkServiceCircuitBreaker").setFallbackUri("forward:/fallback/shorten")))
                        .uri(shortlinkServiceUri))
                .route("shortlink-service-urls", r -> r.path("/api/v1/urls")
                        .filters(f -> f.circuitBreaker(c -> c.setName("shortlinkServiceCircuitBreaker").setFallbackUri("forward:/fallback/urls")))
                        .uri(shortlinkServiceUri))
                .route("shortlink-service-redirect", r -> r.path("/api/v1/{shortCode}")
                        .uri(shortlinkServiceUri))
                .build();
    }
}
