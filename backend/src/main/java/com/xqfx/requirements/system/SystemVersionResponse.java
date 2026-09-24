package com.xqfx.requirements.system;

record SystemVersionResponse(
        Long id,
        Long systemId,
        String systemName,
        String name,
        String description,
        SystemVersionStatus status,
        long requirementCount,
        long recordVersion
) {

    static SystemVersionResponse from(SystemVersionEntity version, long requirementCount) {
        return new SystemVersionResponse(
                version.id(),
                version.system().id(),
                version.system().name(),
                version.name(),
                version.description(),
                version.status(),
                requirementCount,
                version.recordVersion());
    }
}
