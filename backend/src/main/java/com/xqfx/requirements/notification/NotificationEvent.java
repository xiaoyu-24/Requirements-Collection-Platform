package com.xqfx.requirements.notification;

import java.util.Objects;
import java.util.Set;

/** 与投递渠道无关的业务消息事件。 */
public record NotificationEvent(
        NotificationType type,
        Long requirementId,
        String title,
        String content,
        String eventKey,
        Set<NotificationChannel> channels) {

    public NotificationEvent {
        Objects.requireNonNull(type, "消息类型不能为空");
        Objects.requireNonNull(title, "消息标题不能为空");
        Objects.requireNonNull(content, "消息内容不能为空");
        Objects.requireNonNull(eventKey, "消息事件键不能为空");
        channels = Set.copyOf(channels);
    }

    public static NotificationEvent inApp(NotificationType type, Long requirementId,
                                          String title, String content, String eventKey) {
        return new NotificationEvent(type, requirementId, title, content, eventKey,
                Set.of(NotificationChannel.IN_APP));
    }
}
