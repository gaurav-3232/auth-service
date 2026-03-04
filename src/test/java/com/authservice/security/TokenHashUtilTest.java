package com.authservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenHashUtilTest {

    @Test
    @DisplayName("Should produce consistent hash for same input")
    void hashToken_shouldBeConsistent() {
        String token = "my-refresh-token-123";
        String hash1 = TokenHashUtil.hashToken(token);
        String hash2 = TokenHashUtil.hashToken(token);

        assertEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Should produce different hashes for different inputs")
    void hashToken_shouldBeDifferentForDifferentInputs() {
        String hash1 = TokenHashUtil.hashToken("token-a");
        String hash2 = TokenHashUtil.hashToken("token-b");

        assertNotEquals(hash1, hash2);
    }

    @Test
    @DisplayName("Hash should be 64 hex chars (SHA-256)")
    void hashToken_shouldReturn64HexChars() {
        String hash = TokenHashUtil.hashToken("test-token");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }
}
