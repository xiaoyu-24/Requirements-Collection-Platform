package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface RequirementProgressRepository extends JpaRepository<RequirementProgressEntity, Long> {
    List<RequirementProgressEntity> findByRequirement_IdOrderByCreatedAtAscIdAsc(Long requirementId);

    Optional<RequirementProgressEntity> findTopByRequirement_IdOrderByCreatedAtDescIdDesc(Long requirementId);
}
