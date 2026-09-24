package com.xqfx.requirements.requirement;

import org.springframework.data.domain.Page;

import java.util.List;

record RequirementPageResponse(List<RequirementResponse> content, long totalElements, int totalPages, int page, int size) {
    static RequirementPageResponse from(Page<RequirementEntity> result) {
        return new RequirementPageResponse(result.getContent().stream().map(RequirementResponse::from).toList(), result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }
}
