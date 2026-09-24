package com.xqfx.requirements.requirement;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天固定时间检查超期和长期无进展需求，消息事件键会阻止重复发送。 */
@Component
class RequirementReminderScheduler {

    private final RequirementNotificationService notifications;

    RequirementReminderScheduler(RequirementNotificationService notifications) {
        this.notifications = notifications;
    }

    @Scheduled(cron = "${app.notifications.reminder-cron:0 0 9 * * *}", zone = "Asia/Shanghai")
    void scan() {
        notifications.scanReminders();
    }
}
