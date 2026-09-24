package com.xqfx.requirements.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 登录会话。数据库只保存令牌的哈希值，明文令牌仅在签发时返回给浏览器一次。
 * 会话有过期时间，配合定期续期实现"长期保持登录"，同时保证停用账号能在一个短周期内失效。
 */
@Entity
@Table(name = "user_sessions")
class UserSessionEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    protected UserSessionEntity() {
    }

    UserSessionEntity(String tokenHash, UserEntity user, LocalDateTime expiresAt) {
        var now = LocalDateTime.now(ZONE);
        this.tokenHash = tokenHash;
        this.user = user;
        this.createdAt = now;
        this.lastSeenAt = now;
        this.expiresAt = expiresAt;
    }

    UserEntity user() {
        return user;
    }

    boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now(ZONE));
    }

    /** 续期：每次访问把有效期往后推，用户持续使用就不会被登出。 */
    void touch(LocalDateTime expiresAt) {
        this.lastSeenAt = LocalDateTime.now(ZONE);
        this.expiresAt = expiresAt;
    }

    LocalDateTime lastSeenAt() {
        return lastSeenAt;
    }
}
