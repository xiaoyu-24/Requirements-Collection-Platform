package com.xqfx.requirements.notification;

import com.xqfx.requirements.user.UserEntity;

interface NotificationDelivery {
    NotificationChannel channel();

    void deliver(NotificationEvent event, UserEntity recipient);
}
