package com.xqfx.requirements.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    @Modifying
    @Query("delete from UserSessionEntity s where s.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from UserSessionEntity s where s.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
