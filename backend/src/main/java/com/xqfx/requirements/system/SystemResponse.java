package com.xqfx.requirements.system;

import java.util.List;

record SystemResponse(
        Long id,
        String name,
        String ownerName,
        Long ownerUserId,
        SystemStatus status,
        List<String> collaborators,
        List<Long> collaboratorUserIds,
        long recordVersion,
        long versionCount,
        long requirementCount
) {

    static SystemResponse from(SystemEntity system, long versionCount, long requirementCount) {
        return new SystemResponse(
                system.id(),
                system.name(),
                system.ownerName(),
                system.ownerUser() == null ? null : system.ownerUser().id(),
                system.status(),
                system.collaborators(),
                system.collaboratorUsers().stream().map(user -> user.id()).toList(),
                system.recordVersion(),
                versionCount,
                requirementCount
        );
    }
}
