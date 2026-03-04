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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // Generic message to prevent user enumeration
    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or password";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email().toLowerCase())) {
            throw ApiException.conflict("An account with this email already exists");
        }

        UserEntity user = new UserEntity(
                request.email().toLowerCase().trim(),
                passwordEncoder.encode(request.password()),
                request.name(),
                Role.USER
        );
        user = userRepository.save(user);

        return createTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS_MSG));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS_MSG);
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized(INVALID_CREDENTIALS_MSG);
        }

        return createTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.refreshToken());

        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        // Reuse detection: if token was already used/revoked, revoke ALL tokens for user
        if (storedToken.isRevoked()) {
            log.warn("Refresh token reuse detected for user {}. Revoking all tokens.",
                    storedToken.getUser().getId());
            refreshTokenRepository.revokeAllActiveTokensByUserId(storedToken.getUser().getId());
            throw ApiException.unauthorized("Refresh token has been revoked. All sessions terminated for security.");
        }

        if (storedToken.isExpired()) {
            throw ApiException.unauthorized("Refresh token has expired");
        }

        UserEntity user = storedToken.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Account is deactivated");
        }

        // Revoke old token
        storedToken.setRevokedAt(Instant.now());

        // Create new token pair
        AuthResponse response = createTokenPair(user);

        // Link old token to new one
        String newTokenHash = TokenHashUtil.hashToken(response.refreshToken());
        refreshTokenRepository.findByTokenHash(newTokenHash).ifPresent(newToken ->
                storedToken.setReplacedByTokenId(newToken.getId())
        );

        refreshTokenRepository.save(storedToken);

        return response;
    }

    @Transactional
    public MessageResponse logout(RefreshTokenRequest request) {
        String tokenHash = TokenHashUtil.hashToken(request.refreshToken());

        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });

        // Always return success to prevent token probing
        return new MessageResponse("Logged out successfully");
    }

    private AuthResponse createTokenPair(UserEntity user) {
        String accessToken = tokenProvider.generateAccessToken(
                user.getId(), user.getEmail(), user.getRole().name()
        );

        String rawRefreshToken = tokenProvider.generateRefreshTokenValue();
        String hashedRefreshToken = TokenHashUtil.hashToken(rawRefreshToken);

        Instant expiresAt = Instant.now().plusMillis(tokenProvider.getRefreshTokenExpiryMs());

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity(user, hashedRefreshToken, expiresAt);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(accessToken, rawRefreshToken, tokenProvider.getAccessTokenExpiryMs() / 1000);
    }
}
