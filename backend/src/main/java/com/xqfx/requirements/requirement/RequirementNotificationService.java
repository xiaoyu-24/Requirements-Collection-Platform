package com.xqfx.requirements.requirement;

import com.xqfx.requirements.notification.NotificationEvent;
import com.xqfx.requirements.notification.NotificationService;
import com.xqfx.requirements.notification.NotificationType;
import com.xqfx.requirements.user.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;

/** 把需求领域事件转换为站内消息；投递渠道由 NotificationService 处理。 */
@Service
class RequirementNotificationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final RequirementRepository requirements;
    private final RequirementProgressRepository progresses;
    private final NotificationService notifications;
    private final int staleAfterDays;

    RequirementNotificationService(RequirementRepository requirements,
                                   RequirementProgressRepository progresses,
                                   NotificationService notifications,
                                   @Value("${app.notifications.stale-after-days:7}") int staleAfterDays) {
        this.requirements = requirements;
        this.progresses = progresses;
        this.notifications = notifications;
        this.staleAfterDays = Math.max(1, staleAfterDays);
    }

    void onRequirementSubmitted(RequirementEntity requirement, UserEntity actor) {
        var title = titleOf(requirement);
        publishExcludingActor(
                NotificationEvent.inApp(NotificationType.NEW_REQUIREMENT, requirement.id(),
                        "有新的需求待处理", "需求《" + title + "》已提交，请及时处理。",
                        "new-requirement:" + requirement.id()),
                recipientsForNewRequirement(requirement), actor);
    }

    void onAssigneeChanged(RequirementEntity requirement, UserEntity actor) {
        var assignee = requirement.assignee();
        if (assignee == null) {
            return;
        }
        publishExcludingActor(
                NotificationEvent.inApp(NotificationType.ASSIGNED, requirement.id(),
                        "你有新的待处理需求", "需求《" + titleOf(requirement) + "》已指派给你，请及时处理。",
                        "assigned:" + requirement.id() + ":" + assignee.id() + ":" + requirement.recordVersion()),
                java.util.List.of(assignee), actor);
    }

    void onProgressAdded(RequirementEntity requirement, RequirementProgressEntity progress,
                         boolean statusChanged, UserEntity actor) {
        var requester = requirement.requesterUser();
        if (requester == null) {
            return;
        }

        var title = titleOf(requirement);
        var progressSummary = summarizeProgress(progress.content());
        var type = statusChanged ? NotificationType.STATUS_CHANGED : NotificationType.PROGRESS_UPDATED;
        var notificationTitle = statusChanged ? "需求状态和进度已更新" : "需求有新的进度更新";
        var content = statusChanged
                ? "需求《" + title + "》状态已更新为“" + statusLabel(progress.status())
                        + "”。进度：" + progressSummary
                : "需求《" + title + "》有新的进度更新：" + progressSummary;
        var eventKey = (statusChanged ? "status-changed:" : "progress-updated:")
                + requirement.id() + ":" + progress.id();

        publishExcludingActor(
                NotificationEvent.inApp(type, requirement.id(), notificationTitle, truncate(content, 500), eventKey),
                java.util.List.of(requester), actor);
    }

    @Transactional
    void scanReminders() {
        var today = LocalDate.now(ZONE);
        for (var requirement : requirements.findAllByDeletedFalse()) {
            if (!requiresReminder(requirement)) {
                continue;
            }
            var recipients = recipientsForReminder(requirement);
            if (recipients.isEmpty()) {
                continue;
            }

            var periodEnd = requirement.periodEndDate();
            if (periodEnd != null && periodEnd.isBefore(today)) {
                notifications.publish(NotificationEvent.inApp(NotificationType.OVERDUE, requirement.id(),
                                "需求周期已超期", "需求《" + titleOf(requirement) + "》的需求周期已于 "
                                        + periodEnd + " 结束，请及时跟进。",
                                "overdue:" + requirement.id() + ":" + periodEnd), recipients);
            }

            var lastActivity = lastProgressOrSubmissionDate(requirement);
            if (lastActivity != null && !lastActivity.isAfter(today.minusDays(staleAfterDays))) {
                notifications.publish(NotificationEvent.inApp(NotificationType.STALE, requirement.id(),
                                "需求长期无进展", "需求《" + titleOf(requirement) + "》已连续 "
                                        + staleAfterDays + " 天无进展，请及时更新。",
                                "stale:" + requirement.id() + ":" + lastActivity), recipients);
            }
        }
    }

    private Collection<UserEntity> recipientsForNewRequirement(RequirementEntity requirement) {
        return recipientsForSystem(requirement);
    }

    private Collection<UserEntity> recipientsForReminder(RequirementEntity requirement) {
        return recipientsForSystem(requirement);
    }

    private Collection<UserEntity> recipientsForSystem(RequirementEntity requirement) {
        var system = requirement.system();
        if (system == null) {
            return List.of();
        }
        var recipients = new ArrayList<UserEntity>();
        if (system.ownerUser() != null) {
            recipients.add(system.ownerUser());
        }
        recipients.addAll(system.collaboratorUsers());
        return recipients;
    }

    private void publishExcludingActor(NotificationEvent event, Collection<UserEntity> recipients, UserEntity actor) {
        var filtered = new LinkedHashMap<Long, UserEntity>();
        for (var recipient : recipients) {
            if (recipient != null && (actor == null || !recipient.id().equals(actor.id()))) {
                filtered.put(recipient.id(), recipient);
            }
        }
        notifications.publish(event, filtered.values());
    }

    private boolean requiresReminder(RequirementEntity requirement) {
        if (requirement.isDraft() || requirement.status() == null) {
            return false;
        }
        return switch (requirement.status()) {
            case COMPLETED, REJECTED, CLOSED -> false;
            default -> true;
        };
    }

    private LocalDate lastProgressOrSubmissionDate(RequirementEntity requirement) {
        var lastProgress = progresses.findTopByRequirement_IdOrderByCreatedAtDescIdDesc(requirement.id());
        if (lastProgress.isPresent()) {
            return lastProgress.get().createdAt().toLocalDate();
        }
        LocalDateTime submittedAt = requirement.submittedAt();
        return submittedAt == null ? requirement.createdAt().toLocalDate() : submittedAt.toLocalDate();
    }

    private static String titleOf(RequirementEntity requirement) {
        return requirement.title() == null || requirement.title().isBlank() ? "未命名需求" : requirement.title();
    }

    private static String summarizeProgress(String content) {
        var normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.isEmpty() ? "已更新需求进度。" : truncate(normalized, 200);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 1) + "…";
    }

    private static String statusLabel(RequirementStatus status) {
        return switch (status) {
            case PENDING_EVALUATION -> "待评估";
            case CONFIRMED -> "已确认";
            case IN_DEVELOPMENT -> "开发中";
            case PAUSED -> "暂停";
            case COMPLETED -> "已完成";
            case REJECTED -> "已拒绝";
            case CLOSED -> "已关闭";
        };
    }
}
