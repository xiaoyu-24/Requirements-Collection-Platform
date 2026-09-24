package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface RequirementVersionChangeRepository extends JpaRepository<RequirementVersionChangeEntity, Long> {
    List<RequirementVersionChangeEntity> findByRequirement_IdOrderByCreatedAtAscIdAsc(Long requirementId);
}
