package com.link.shortlinkx.gateway.config;

import com.link.shortlinkx.gateway.security.JwtReactiveAuthenticationManager;
import com.link.shortlinkx.gateway.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Stateless JWT security. Everything is denied unless it is explicitly listed as public below,
 * so a new route is protected by default.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private static final String SHORT_CODE = "/api/v1/{shortCode:[a-zA-Z0-9]{1,10}}";

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, JwtUtils jwtUtils) {
        HttpStatusServerEntryPoint unauthorized = new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED);

        AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(new JwtReactiveAuthenticationManager(jwtUtils));
        jwtFilter.setServerAuthenticationConverter(bearerTokenConverter());
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());
        jwtFilter.setAuthenticationFailureHandler(new ServerAuthenticationEntryPointFailureHandler(unauthorized));
        // Login/register must work even if the browser still holds a stale token.
        jwtFilter.setRequiresAuthenticationMatcher(
                new NegatedServerWebExchangeMatcher(ServerWebExchangeMatchers.pathMatchers("/api/v1/auth/**")));

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .pathMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // Declared before the redirect rule, which would otherwise also match these literals.
                        .pathMatchers("/api/v1/shorten", "/api/v1/urls").authenticated()
                        .pathMatchers(HttpMethod.GET, SHORT_CODE).permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }

    private ServerAuthenticationConverter bearerTokenConverter() {
        return exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return Mono.empty();
            }
            return Mono.just(UsernamePasswordAuthenticationToken.unauthenticated(null, header.substring(7).trim()));
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Correlation-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-ID"));
        // The JWT travels in the Authorization header, not in cookies, so credentials are not needed.
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
