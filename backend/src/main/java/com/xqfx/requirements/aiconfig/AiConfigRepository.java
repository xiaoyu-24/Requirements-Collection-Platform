package com.xqfx.requirements.aiconfig;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AiConfigRepository extends JpaRepository<AiConfigEntity, Long> {
    Optional<AiConfigEntity> findByIsActiveTrue();
    List<AiConfigEntity> findAllByOrderByIdAsc();
}
