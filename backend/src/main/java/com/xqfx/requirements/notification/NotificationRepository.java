package com.xqfx.requirements.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    Page<NotificationEntity> findByRecipient_IdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<NotificationEntity> findByRecipient_IdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Optional<NotificationEntity> findByIdAndRecipient_Id(Long id, Long recipientId);

    List<NotificationEntity> findByRecipient_IdAndReadAtIsNull(Long recipientId);

    long countByRecipient_IdAndReadAtIsNull(Long recipientId);

    boolean existsByRecipient_IdAndEventKey(Long recipientId, String eventKey);
}
