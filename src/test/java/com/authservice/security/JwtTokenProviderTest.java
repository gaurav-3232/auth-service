package com.authservice.security;

import com.authservice.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-at-least-256-bits-long-for-HS256-algorithm-testing!!",
                900000L,  // 15 min
                604800000L // 7 days
        );
        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("Should generate valid access token")
    void generateAccessToken_shouldReturnNonNullToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "test@example.com", "USER");

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("Should validate a correctly signed token")
    void validateToken_shouldReturnTrueForValidToken() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "test@example.com", "USER");

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("Should reject a tampered token")
    void validateToken_shouldReturnFalseForTamperedToken() {
        assertFalse(tokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    @DisplayName("Should reject null token")
    void validateToken_shouldReturnFalseForNullToken() {
        assertFalse(tokenProvider.validateToken(null));
    }

    @Test
    @DisplayName("Should extract correct user ID from token")
    void getUserIdFromToken_shouldReturnCorrectUserId() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "test@example.com", "ADMIN");

        UUID extracted = tokenProvider.getUserIdFromToken(token);
        assertEquals(userId, extracted);
    }

    @Test
    @DisplayName("Should extract correct role from token")
    void getRoleFromToken_shouldReturnCorrectRole() {
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.generateAccessToken(userId, "test@example.com", "ADMIN");

        String role = tokenProvider.getRoleFromToken(token);
        assertEquals("ADMIN", role);
    }

    @Test
    @DisplayName("Should generate unique refresh token values")
    void generateRefreshTokenValue_shouldBeUnique() {
        String token1 = tokenProvider.generateRefreshTokenValue();
        String token2 = tokenProvider.generateRefreshTokenValue();

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Should reject expired token")
    void validateToken_shouldRejectExpiredToken() {
        // Create provider with 0ms expiry
        JwtProperties properties = new JwtProperties(
                "test-secret-key-that-is-at-least-256-bits-long-for-HS256-algorithm-testing!!",
                0L, 0L
        );
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(properties);

        UUID userId = UUID.randomUUID();
        String token = shortLivedProvider.generateAccessToken(userId, "test@example.com", "USER");

        assertFalse(shortLivedProvider.validateToken(token));
    }
}
