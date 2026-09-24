package com.xqfx.requirements.aiconfig;

import java.time.LocalDate;

record AiAnalysisResponse(
        String requesterName,
        Long departmentId,
        String department,
        String title,
        Long typeId,
        String type,
        String content,
        Long systemId,
        Long targetVersionId,
        LocalDate periodStartDate,
        LocalDate periodEndDate
) {
}
