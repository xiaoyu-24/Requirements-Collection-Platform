package com.xqfx.requirements.requirement;

import java.time.LocalDateTime;

record RequirementVersionChangeResponse(
        Long id,
        Long requirementId,
        Long fromVersionId,
        String fromVersionName,
        Long toVersionId,
        String toVersionName,
        RequirementVersionChangeAction action,
        Long operatorUserId,
        String operatorName,
        LocalDateTime createdAt) {

    static RequirementVersionChangeResponse from(RequirementVersionChangeEntity change) {
        return new RequirementVersionChangeResponse(
                change.id(),
                change.requirement().id(),
                change.fromVersion() == null ? null : change.fromVersion().id(),
                change.fromVersionName(),
                change.toVersion() == null ? null : change.toVersion().id(),
                change.toVersionName(),
                change.action(),
                change.operator().id(),
                change.operatorName(),
                change.createdAt());
    }
}
