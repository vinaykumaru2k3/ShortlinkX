package com.link.shortlinkx.gateway.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "mySuperDuperSecretKeyMustBeVeryLongToAvoidSecurityExceptionsAndNeedAtLeast256BitsOfEntropy!");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 3600000L); // 1 hour
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
}
