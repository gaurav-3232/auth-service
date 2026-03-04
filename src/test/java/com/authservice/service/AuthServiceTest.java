package com.authservice.service;

import com.authservice.domain.entity.RefreshTokenEntity;
import com.authservice.domain.entity.Role;
import com.authservice.domain.entity.UserEntity;
import com.authservice.domain.entity.UserStatus;
import com.authservice.dto.request.LoginRequest;
import com.authservice.dto.request.RefreshTokenRequest;
import com.authservice.dto.request.RegisterRequest;
import com.authservice.dto.response.AuthResponse;
import com.authservice.dto.response.MessageResponse;
import com.authservice.exception.ApiException;
import com.authservice.repository.RefreshTokenRepository;
import com.authservice.repository.UserRepository;
import com.authservice.security.JwtTokenProvider;
import com.authservice.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserEntity("test@example.com", "hashed-password", "Test User", Role.USER);
        testUser.setId(UUID.randomUUID());
        testUser.setStatus(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Register should create user and return tokens")
    void register_success() {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password123!", "Test User");

        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any(UserEntity.class))).thenReturn(testUser);
        when(tokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(tokenProvider.generateRefreshTokenValue()).thenReturn("refresh-token");
        when(tokenProvider.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(tokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(new RefreshTokenEntity());

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    @DisplayName("Register should fail for duplicate email")
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest("test@example.com", "Password123!", "Test User");
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.register(request));
        assertEquals(409, ex.getStatus().value());
    }

    @Test
    @DisplayName("Login should return tokens for valid credentials")
    void login_success() {
        LoginRequest request = new LoginRequest("test@example.com", "Password123!");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(tokenProvider.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(tokenProvider.generateRefreshTokenValue()).thenReturn("refresh-token");
        when(tokenProvider.getRefreshTokenExpiryMs()).thenReturn(604800000L);
        when(tokenProvider.getAccessTokenExpiryMs()).thenReturn(900000L);
        when(refreshTokenRepository.save(any())).thenReturn(new RefreshTokenEntity());

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
    }

    @Test
    @DisplayName("Login should fail for wrong password")
    void login_wrongPassword_throws() {
        LoginRequest request = new LoginRequest("test@example.com", "wrong-password");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(401, ex.getStatus().value());
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Login should fail for non-existent email with same error")
    void login_nonExistentEmail_throws() {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "password");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(401, ex.getStatus().value());
        assertEquals("Invalid email or password", ex.getMessage());
    }

    @Test
    @DisplayName("Login should fail for deactivated user")
    void login_deactivatedUser_throws() {
        testUser.setStatus(UserStatus.DEACTIVATED);
        LoginRequest request = new LoginRequest("test@example.com", "Password123!");

        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () -> authService.login(request));
        assertEquals(401, ex.getStatus().value());
    }

    @Test
    @DisplayName("Refresh with revoked token should trigger reuse detection")
    void refresh_reuseDetection_revokesAllTokens() {
        String rawToken = "some-refresh-token";
        RefreshTokenRequest request = new RefreshTokenRequest(rawToken);

        RefreshTokenEntity revokedToken = new RefreshTokenEntity(
                testUser,
                TokenHashUtil.hashToken(rawToken),
                Instant.now().plusSeconds(3600)
        );
        revokedToken.setRevokedAt(Instant.now().minusSeconds(60)); // already revoked

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedToken));

        ApiException ex = assertThrows(ApiException.class, () -> authService.refresh(request));
        assertEquals(401, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("revoked"));
        verify(refreshTokenRepository).revokeAllActiveTokensByUserId(testUser.getId());
    }

    @Test
    @DisplayName("Logout should always return success")
    void logout_success() {
        RefreshTokenRequest request = new RefreshTokenRequest("any-token");
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        MessageResponse response = authService.logout(request);

        assertEquals("Logged out successfully", response.message());
    }
}
