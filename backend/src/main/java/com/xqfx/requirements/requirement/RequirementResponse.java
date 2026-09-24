package com.xqfx.requirements.requirement;

import java.time.LocalDate;
import java.time.LocalDateTime;

record RequirementResponse(
        Long id,
        String requesterName,
        Long requesterUserId,
        Long departmentId,
        String department,
        String title,
        Long typeId,
        String type,
        RequirementUrgency urgency,
        String content,
        Long systemId,
        String systemName,
        String systemOwnerName,
        RequirementSaveType saveType,
        RequirementStatus status,
        Long targetVersionId,
        String targetVersionName,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime statusUpdatedAt,
        LocalDateTime completedAt,
        String handledBy,
        String completionDescription,
        Long assigneeId,
        String assigneeName,
        long recordVersion) {

    static RequirementResponse from(RequirementEntity requirement) {
        var department = requirement.department();
        var type = requirement.type();
        var requesterUser = requirement.requesterUser();
        var assignee = requirement.assignee();
        var system = requirement.system();
        var systemOwner = system == null ? null : system.ownerUser();
        return new RequirementResponse(
                requirement.id(),
                requirement.requesterName(),
                requesterUser == null ? null : requesterUser.id(),
                department == null ? null : department.id(),
                department == null ? null : department.name(),
                requirement.title(),
                type == null ? null : type.id(),
                type == null ? null : type.name(),
                requirement.urgency(),
                requirement.content(),
                requirement.system() == null ? null : requirement.system().id(),
                requirement.system() == null ? null : requirement.system().name(),
                system == null ? null : systemOwner == null ? system.ownerName() : systemOwner.displayName(),
                requirement.saveType(),
                requirement.status(),
                requirement.targetVersion() == null ? null : requirement.targetVersion().id(),
                requirement.targetVersion() == null ? null : requirement.targetVersion().name(),
                requirement.periodStartDate(),
                requirement.periodEndDate(),
                requirement.createdAt(),
                requirement.updatedAt(),
                requirement.submittedAt(),
                requirement.statusUpdatedAt(),
                requirement.completedAt(),
                requirement.handledBy(),
                requirement.completionDescription(),
                assignee == null ? null : assignee.id(),
                assignee == null ? null : assignee.displayName(),
                requirement.recordVersion());
    }
}
