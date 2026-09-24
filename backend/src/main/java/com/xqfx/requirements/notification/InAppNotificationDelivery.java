package com.xqfx.requirements.notification;

import com.xqfx.requirements.user.UserEntity;
import org.springframework.stereotype.Component;

@Component
class InAppNotificationDelivery implements NotificationDelivery {

    private final NotificationRepository notifications;

    InAppNotificationDelivery(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationEvent event, UserEntity recipient) {
        if (!recipient.canReceiveNotifications()) {
            return;
        }
        if (notifications.existsByRecipient_IdAndEventKey(recipient.id(), event.eventKey())) {
            return;
        }
        notifications.save(new NotificationEntity(recipient, event, channel()));
    }
}
