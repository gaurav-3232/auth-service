package com.authservice.service;

import com.authservice.domain.entity.Role;
import com.authservice.domain.entity.UserEntity;
import com.authservice.domain.entity.UserStatus;
import com.authservice.dto.request.RoleUpdateRequest;
import com.authservice.dto.response.UserResponse;
import com.authservice.exception.ApiException;
import com.authservice.repository.RefreshTokenRepository;
import com.authservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public UserService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::fromEntity);
    }

    @Transactional
    public UserResponse updateUserRole(UUID userId, RoleUpdateRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        user.setRole(request.role());
        user = userRepository.save(user);

        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse deactivateUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));

        if (user.getStatus() == UserStatus.DEACTIVATED) {
            throw ApiException.badRequest("User is already deactivated");
        }

        user.setStatus(UserStatus.DEACTIVATED);
        user = userRepository.save(user);

        // Revoke all active refresh tokens
        refreshTokenRepository.revokeAllActiveTokensByUserId(userId);

        return UserResponse.fromEntity(user);
    }
}
