package com.xqfx.requirements.notification;

import com.xqfx.requirements.user.UserEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final List<NotificationDelivery> deliveries;

    NotificationService(NotificationRepository notifications, List<NotificationDelivery> deliveries) {
        this.notifications = notifications;
        this.deliveries = deliveries;
    }

    /** 按事件声明的渠道投递；新增渠道只需增加对应的 NotificationDelivery 实现。 */
    @Transactional
    public void publish(NotificationEvent event, Collection<UserEntity> recipients) {
        var uniqueRecipients = new LinkedHashMap<Long, UserEntity>();
        for (var recipient : recipients) {
            if (recipient != null && !recipient.isDisabled() && recipient.canReceiveNotifications()) {
                uniqueRecipients.put(recipient.id(), recipient);
            }
        }

        for (var recipient : uniqueRecipients.values()) {
            for (var delivery : deliveries) {
                if (event.channels().contains(delivery.channel())) {
                    delivery.deliver(event, recipient);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse listFor(UserEntity recipient, int page, int size, boolean unreadOnly) {
        assertCanReceiveNotifications(recipient);
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = unreadOnly
                ? notifications.findByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(recipient.id(), pageable)
                : notifications.findByRecipient_IdOrderByCreatedAtDesc(recipient.id(), pageable);
        return NotificationPageResponse.from(result);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UserEntity recipient) {
        assertCanReceiveNotifications(recipient);
        return notifications.countByRecipient_IdAndReadAtIsNull(recipient.id());
    }

    @Transactional
    public NotificationResponse markRead(UserEntity recipient, Long notificationId) {
        assertCanReceiveNotifications(recipient);
        var notification = notifications.findByIdAndRecipient_Id(notificationId, recipient.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消息不存在"));
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(UserEntity recipient) {
        assertCanReceiveNotifications(recipient);
        notifications.findByRecipient_IdAndReadAtIsNull(recipient.id())
                .forEach(NotificationEntity::markRead);
    }

    private static void assertCanReceiveNotifications(UserEntity recipient) {
        if (!recipient.canReceiveNotifications()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不可使用站内消息");
        }
    }
}
