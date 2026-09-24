package com.xqfx.requirements.requirement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "requirement_progresses")
class RequirementProgressEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private RequirementEntity requirement;

    @Column(nullable = false, length = 10000)
    private String content;

    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 50)
    private String authorName;

    @Enumerated(EnumType.STRING)
    private RequirementStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected RequirementProgressEntity() {
    }

    RequirementProgressEntity(RequirementEntity requirement, Long authorId, String authorName,
                              String content, RequirementStatus status) {
        this.requirement = requirement;
        this.authorId = authorId;
        this.authorName = authorName;
        this.content = content;
        this.status = status;
    }

    Long id() {
        return id;
    }

    String content() {
        return content;
    }

    Long authorId() {
        return authorId;
    }

    String authorName() {
        return authorName;
    }

    RequirementStatus status() {
        return status;
    }

    LocalDateTime createdAt() {
        return createdAt;
    }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now(ZONE);
    }
}
