package com.xqfx.requirements.system;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SystemRepository extends JpaRepository<SystemEntity, Long> {
    List<SystemEntity> findAllByDeletedFalse();
    Optional<SystemEntity> findByIdAndDeletedFalse(Long id);
    boolean existsByActiveNameKey(String activeNameKey);
    boolean existsByActiveNameKeyAndIdNot(String activeNameKey, Long id);

    boolean existsByOwnerUser_IdAndDeletedFalse(Long userId);

    @Query("select case when count(system) > 0 then true else false end from SystemEntity system join system.collaboratorUsers collaborator where collaborator.id = :userId and system.deleted = false")
    boolean existsByCollaboratorUserIdAndDeletedFalse(Long userId);
}
