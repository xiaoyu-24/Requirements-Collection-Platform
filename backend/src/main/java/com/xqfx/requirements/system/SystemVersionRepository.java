package com.xqfx.requirements.system;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SystemVersionRepository extends JpaRepository<SystemVersionEntity, Long> {

    List<SystemVersionEntity> findBySystemIdAndDeletedFalseOrderByNameAsc(Long systemId);

    long countBySystemIdAndDeletedFalse(Long systemId);

    Optional<SystemVersionEntity> findByIdAndDeletedFalse(Long id);

    boolean existsBySystemIdAndActiveNameKey(Long systemId, String activeNameKey);

    boolean existsBySystemIdAndActiveNameKeyAndIdNot(Long systemId, String activeNameKey, Long id);
}
