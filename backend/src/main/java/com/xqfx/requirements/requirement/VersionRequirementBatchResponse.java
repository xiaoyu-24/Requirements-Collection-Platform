package com.xqfx.requirements.requirement;

record VersionRequirementBatchResponse(int boundCount, int migratedCount, int unboundCount) {
    static VersionRequirementBatchResponse bound(int boundCount, int migratedCount) {
        return new VersionRequirementBatchResponse(boundCount, migratedCount, 0);
    }

    static VersionRequirementBatchResponse unbound(int unboundCount) {
        return new VersionRequirementBatchResponse(0, 0, unboundCount);
    }
}
