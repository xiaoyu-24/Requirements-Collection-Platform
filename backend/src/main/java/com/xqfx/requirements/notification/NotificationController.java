package com.xqfx.requirements.notification;

import com.xqfx.requirements.user.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    NotificationPageResponse list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return notifications.listFor(CurrentUser.require(), page, size, unreadOnly);
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount() {
        return Map.of("count", notifications.unreadCount(CurrentUser.require()));
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@PathVariable Long id) {
        return notifications.markRead(CurrentUser.require(), id);
    }

    @PatchMapping("/read-all")
    void markAllRead() {
        notifications.markAllRead(CurrentUser.require());
    }
}
