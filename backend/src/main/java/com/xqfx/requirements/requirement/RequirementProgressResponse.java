package com.xqfx.requirements.requirement;

import java.time.LocalDateTime;

record RequirementProgressResponse(
        Long id,
        String content,
        Long authorId,
        String authorName,
        RequirementStatus status,
        LocalDateTime createdAt) {

    static RequirementProgressResponse from(RequirementProgressEntity progress) {
        return new RequirementProgressResponse(
                progress.id(),
                progress.content(),
                progress.authorId(),
                progress.authorName(),
                progress.status(),
                progress.createdAt());
    }
}
