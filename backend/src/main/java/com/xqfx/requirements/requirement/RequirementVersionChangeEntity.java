package com.xqfx.requirements.requirement;

import com.xqfx.requirements.system.SystemVersionEntity;
import com.xqfx.requirements.user.UserEntity;
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
@Table(name = "requirement_version_changes")
class RequirementVersionChangeEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requirement_id", nullable = false)
    private RequirementEntity requirement;

    @ManyToOne
    @JoinColumn(name = "from_version_id")
    private SystemVersionEntity fromVersion;

    @ManyToOne
    @JoinColumn(name = "to_version_id")
    private SystemVersionEntity toVersion;

    @Column(length = 100)
    private String fromVersionName;

    @Column(length = 100)
    private String toVersionName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequirementVersionChangeAction action;

    @ManyToOne(optional = false)
    @JoinColumn(name = "operator_user_id", nullable = false)
    private UserEntity operator;

    @Column(nullable = false, length = 50)
    private String operatorName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected RequirementVersionChangeEntity() {
    }

    RequirementVersionChangeEntity(RequirementEntity requirement, SystemVersionEntity fromVersion,
                                   SystemVersionEntity toVersion, RequirementVersionChangeAction action,
                                   UserEntity operator) {
        this.requirement = requirement;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.fromVersionName = fromVersion == null ? null : fromVersion.name();
        this.toVersionName = toVersion == null ? null : toVersion.name();
        this.action = action;
        this.operator = operator;
        this.operatorName = operator.displayName();
    }

    Long id() { return id; }
    RequirementEntity requirement() { return requirement; }
    SystemVersionEntity fromVersion() { return fromVersion; }
    SystemVersionEntity toVersion() { return toVersion; }
    String fromVersionName() { return fromVersionName; }
    String toVersionName() { return toVersionName; }
    RequirementVersionChangeAction action() { return action; }
    UserEntity operator() { return operator; }
    String operatorName() { return operatorName; }
    LocalDateTime createdAt() { return createdAt; }

    @PrePersist
    void setCreatedAt() {
        createdAt = LocalDateTime.now(ZONE);
    }
}
