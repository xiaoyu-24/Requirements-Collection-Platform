package com.xqfx.requirements.notification;

import com.xqfx.requirements.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "notifications")
class NotificationEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private UserEntity recipient;

    private Long requirementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, length = 120)
    private String eventKey;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected NotificationEntity() {
    }

    NotificationEntity(UserEntity recipient, NotificationEvent event, NotificationChannel channel) {
        this.recipient = recipient;
        this.requirementId = event.requirementId();
        this.type = event.type();
        this.channel = channel;
        this.title = event.title();
        this.content = event.content();
        this.eventKey = event.eventKey();
    }

    Long id() {
        return id;
    }

    Long requirementId() {
        return requirementId;
    }

    NotificationType type() {
        return type;
    }

    String title() {
        return title;
    }

    String content() {
        return content;
    }

    LocalDateTime readAt() {
        return readAt;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    void markRead() {
        if (readAt == null) {
            readAt = LocalDateTime.now(ZONE);
        }
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now(ZONE);
    }
}
