package com.xqfx.requirements.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Long requirementId,
        String title,
        String content,
        boolean read,
        LocalDateTime createdAt) {

    static NotificationResponse from(NotificationEntity notification) {
        return new NotificationResponse(
                notification.id(),
                notification.type(),
                notification.requirementId(),
                notification.title(),
                notification.content(),
                notification.readAt() != null,
                notification.createdAt());
    }
}
