package com.authservice.repository;

import com.authservice.domain.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshTokenEntity rt SET rt.revokedAt = CURRENT_TIMESTAMP " +
           "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    int revokeAllActiveTokensByUserId(@Param("userId") UUID userId);
}
