package com.xqfx.requirements.requirement;

record VersionRequirementSummaryResponse(
        long currentCount,
        long candidateCount,
        long unassignedCount,
        long otherVersionCount,
        long highUrgencyCount) {
}
