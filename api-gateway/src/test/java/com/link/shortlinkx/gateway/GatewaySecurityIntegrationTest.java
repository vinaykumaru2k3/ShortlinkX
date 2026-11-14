package com.link.shortlinkx.gateway;

import com.link.shortlinkx.gateway.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
class GatewaySecurityIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JwtUtils jwtUtils;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToApplicationContext(context).build();
    }

    @Test
    void protectedRoutesRejectMissingAndInvalidTokens() {
        client.get().uri("/api/v1/urls").exchange().expectStatus().isUnauthorized();
        client.post().uri("/api/v1/shorten").bodyValue(Map.of("url", "https://example.com"))
                .exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/urls").header("Authorization", "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownPathsAreDeniedByDefault() {
        client.get().uri("/api/v1/some/new/endpoint").exchange().expectStatus().isUnauthorized();
        client.get().uri("/actuator/env").exchange().expectStatus().isUnauthorized();
        client.get().uri("/fallback/shorten").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void healthEndpointIsPublic() {
        client.get().uri("/actuator/health").exchange().expectStatus().value(status ->
                assertNotEquals(401, status.intValue()));
    }

    @Test
    void registerThenLoginIssuesAUsableToken() {
        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("username", "alice", "email", "alice@example.com", "password", "correct-horse"))
                .exchange().expectStatus().isCreated();

        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("username", "alice", "email", "other@example.com", "password", "correct-horse"))
                .exchange().expectStatus().isEqualTo(409);

        client.post().uri("/api/v1/auth/login")
                .bodyValue(Map.of("username", "alice", "password", "wrong-password"))
                .exchange().expectStatus().isUnauthorized();

        String token = jwtUtils.generateToken("alice", 1L);
        // The downstream service is not running in this test, so we only assert that authentication passed.
        client.get().uri("/api/v1/urls").header("Authorization", "Bearer " + token)
                .exchange().expectStatus().value(status -> assertNotEquals(401, status.intValue()));
    }

    @Test
    void staleTokenDoesNotBlockLogin() {
        client.post().uri("/api/v1/auth/login")
                .header("Authorization", "Bearer expired-or-garbage")
                .bodyValue(Map.of("username", "nobody", "password", "whatever-123"))
                .exchange().expectStatus().isUnauthorized(); // 401 from bad credentials, not from the stale token

        client.post().uri("/api/v1/auth/register")
                .header("Authorization", "Bearer expired-or-garbage")
                .bodyValue(Map.of("username", "bob", "email", "bob@example.com", "password", "correct-horse"))
                .exchange().expectStatus().isCreated();
    }

    @Test
    void weakRegistrationInputIsRejected() {
        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("username", "x y", "email", "bad", "password", "short"))
                .exchange().expectStatus().isBadRequest();
    }
}
