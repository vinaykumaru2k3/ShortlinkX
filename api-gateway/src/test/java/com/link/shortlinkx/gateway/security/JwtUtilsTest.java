package com.link.shortlinkx.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private static final String SECRET = "test-only-secret-that-is-at-least-thirty-two-bytes-long";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = newJwtUtils(SECRET, 3600000L);
    }

    private static JwtUtils newJwtUtils(String secret, long expirationMs) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "jwtExpirationMs", expirationMs);
        return utils;
    }

    @Test
    void testTokenGenerationAndValidation() {
        String token = jwtUtils.generateToken("testuser", 42L);
        assertNotNull(token);

        assertTrue(jwtUtils.validateToken(token));
        assertEquals("testuser", jwtUtils.getUsernameFromToken(token));
        assertEquals(42L, jwtUtils.getUserIdFromToken(token));
    }

    @Test
    void testInvalidToken() {
        assertFalse(jwtUtils.validateToken("invalidTokenHeader.invalidTokenPayload.invalidTokenSignature"));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String forged = newJwtUtils("a-completely-different-secret-of-sufficient-length", 3600000L)
                .generateToken("attacker", 1L);

        assertFalse(jwtUtils.validateToken(forged));
        assertNull(jwtUtils.getUserIdFromToken(forged));
    }

    @Test
    void rejectsExpiredToken() {
        String expired = newJwtUtils(SECRET, -1000L).generateToken("testuser", 42L);

        assertFalse(jwtUtils.validateToken(expired));
    }

    @Test
    void failsStartupWhenSecretIsMissingOrTooShort() {
        assertThrows(IllegalStateException.class, () -> newJwtUtils("short", 1000L).validateConfiguration());
        assertThrows(IllegalStateException.class, () -> newJwtUtils(null, 1000L).validateConfiguration());
        assertDoesNotThrow(() -> jwtUtils.validateConfiguration());
    }
}
