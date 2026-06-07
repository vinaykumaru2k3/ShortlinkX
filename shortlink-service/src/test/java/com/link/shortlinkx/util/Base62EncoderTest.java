package com.link.shortlinkx.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    @Test
    void testGenerateRandomCode_Length() {
        String code = Base62Encoder.generateRandomCode(6);
        assertNotNull(code);
        assertEquals(6, code.length());
        
        String longerCode = Base62Encoder.generateRandomCode(10);
        assertEquals(10, longerCode.length());
    }

    @Test
    void testGenerateRandomCode_Characters() {
        String code = Base62Encoder.generateRandomCode(100);
        assertTrue(code.matches("^[a-zA-Z0-9]+$"));
    }
}
