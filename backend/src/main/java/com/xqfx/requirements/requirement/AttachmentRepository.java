package com.xqfx.requirements.requirement;

import org.springframework.data.jpa.repository.JpaRepository;

interface AttachmentRepository extends JpaRepository<AttachmentEntity, Long> { java.util.Optional<AttachmentEntity> findByIdAndDeletedFalse(Long id); java.util.List<AttachmentEntity> findByRequirementIdAndDeletedFalseOrderByIdAsc(Long requirementId); }
