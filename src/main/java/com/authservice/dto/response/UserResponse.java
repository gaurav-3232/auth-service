package com.authservice.dto.response;

import com.authservice.domain.entity.Role;
import com.authservice.domain.entity.UserEntity;
import com.authservice.domain.entity.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String name,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse fromEntity(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
