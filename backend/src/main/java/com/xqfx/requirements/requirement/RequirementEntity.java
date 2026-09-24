package com.xqfx.requirements.requirement;

import com.xqfx.requirements.dictionary.DictionaryItemEntity;
import com.xqfx.requirements.system.SystemEntity;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "requirements")
class RequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long recordVersion;

    private String requesterName;

    @ManyToOne
    @JoinColumn(name = "requester_user_id")
    private UserEntity requesterUser;

    @ManyToOne
    @JoinColumn(name = "assignee_user_id")
    private UserEntity assignee;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private DictionaryItemEntity department;

    private String title;

    @ManyToOne
    @JoinColumn(name = "type_id")
    private DictionaryItemEntity type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementUrgency urgency = RequirementUrgency.MEDIUM;

    @Column(length = 10000)
    private String content;

    @ManyToOne
    private SystemEntity system;

    @ManyToOne
    private SystemVersionEntity targetVersion;

    private LocalDate periodStartDate;
    private LocalDate periodEndDate;

    @Enumerated(EnumType.STRING)
    private RequirementStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequirementSaveType saveType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime submittedAt;
    private LocalDateTime statusUpdatedAt;
    private LocalDateTime completedAt;
    private String handledBy;

    @Column(length = 10000)
    private String completionDescription;

    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    protected RequirementEntity() {
    }

    RequirementEntity(UserEntity requesterUser, String requesterName, DictionaryItemEntity department, String title,
                      DictionaryItemEntity type, String content, SystemEntity system,
                      SystemVersionEntity targetVersion, RequirementPeriod period, RequirementUrgency urgency) {
        this.requesterUser = requesterUser;
        this.requesterName = requesterName;
        this.department = department;
        this.title = title;
        this.type = type;
        this.content = content;
        this.system = system;
        this.targetVersion = targetVersion;
        this.periodStartDate = period.startDate();
        this.periodEndDate = period.endDate();
        this.urgency = normalizeUrgency(urgency);
        this.saveType = RequirementSaveType.SUBMITTED;
        this.status = RequirementStatus.PENDING_EVALUATION;
    }

    static RequirementEntity draft(UserEntity requesterUser, String requesterName, DictionaryItemEntity department, String title,
                                   DictionaryItemEntity type, String content, SystemEntity system,
                                   SystemVersionEntity targetVersion, RequirementPeriod period, RequirementUrgency urgency) {
        var draft = new RequirementEntity();
        draft.requesterUser = requesterUser;
        draft.requesterName = requesterName;
        draft.department = department;
        draft.title = title;
        draft.type = type;
        draft.content = content;
        draft.system = system;
        draft.targetVersion = targetVersion;
        draft.periodStartDate = period.startDate();
        draft.periodEndDate = period.endDate();
        draft.urgency = normalizeUrgency(urgency);
        draft.saveType = RequirementSaveType.DRAFT;
        return draft;
    }

    Long id() { return id; }
    long recordVersion() { return recordVersion; }
    String requesterName() { return requesterName; }
    UserEntity requesterUser() { return requesterUser; }
    UserEntity assignee() { return assignee; }
    DictionaryItemEntity department() { return department; }
    String title() { return title; }
    DictionaryItemEntity type() { return type; }
    RequirementUrgency urgency() { return urgency; }
    String content() { return content; }
    SystemEntity system() { return system; }
    SystemVersionEntity targetVersion() { return targetVersion; }
    LocalDate periodStartDate() { return periodStartDate; }
    LocalDate periodEndDate() { return periodEndDate; }
    RequirementStatus status() { return status; }
    RequirementSaveType saveType() { return saveType; }
    LocalDateTime createdAt() { return createdAt; }
    LocalDateTime updatedAt() { return updatedAt; }
    LocalDateTime submittedAt() { return submittedAt; }
    LocalDateTime statusUpdatedAt() { return statusUpdatedAt; }
    LocalDateTime completedAt() { return completedAt; }
    String handledBy() { return handledBy; }
    String completionDescription() { return completionDescription; }

    void updateStatusFromProgress(RequirementStatus status) {
        this.status = status;
    }

    void assign(UserEntity assignee) {
        this.assignee = assignee;
    }

    void changeTargetVersion(SystemVersionEntity targetVersion) {
        this.targetVersion = targetVersion;
    }

    void migrateSystem(SystemEntity targetSystem) {
        this.system = targetSystem;
        this.targetVersion = null;
    }

    void linkRequesterUserIfMissing(UserEntity requesterUser) {
        if (this.requesterUser == null) {
            this.requesterUser = requesterUser;
        }
    }

    void update(String requesterName, DictionaryItemEntity department, String title,
                DictionaryItemEntity type, String content, SystemEntity system,
                RequirementPeriod period, RequirementUrgency urgency) {
        var systemChanged = !sameSystem(this.system, system);
        this.requesterName = requesterName;
        this.department = department;
        this.title = title;
        this.type = type;
        this.content = content;
        this.system = system;
        if (systemChanged) this.targetVersion = null;
        this.periodStartDate = period.startDate();
        this.periodEndDate = period.endDate();
        this.urgency = normalizeUrgency(urgency);
        if (isDraft()) {
            this.saveType = RequirementSaveType.SUBMITTED;
            this.submittedAt = now();
            this.status = RequirementStatus.PENDING_EVALUATION;
        }
    }

    boolean isDraft() {
        return saveType == RequirementSaveType.DRAFT;
    }

    void updateDraft(String requesterName, DictionaryItemEntity department, String title,
                     DictionaryItemEntity type, String content, SystemEntity system,
                     RequirementPeriod period, RequirementUrgency urgency) {
        var systemChanged = !sameSystem(this.system, system);
        this.requesterName = requesterName;
        this.department = department;
        this.title = title;
        this.type = type;
        this.content = content;
        this.system = system;
        if (systemChanged) this.targetVersion = null;
        this.periodStartDate = period.startDate();
        this.periodEndDate = period.endDate();
        this.urgency = normalizeUrgency(urgency);
    }

    void delete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    @PrePersist
    void setInitialTimestamps() {
        var now = now();
        createdAt = now;
        updatedAt = now;
        if (saveType == RequirementSaveType.SUBMITTED) {
            submittedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = now();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    }

    private static RequirementUrgency normalizeUrgency(RequirementUrgency urgency) {
        return urgency == null ? RequirementUrgency.MEDIUM : urgency;
    }

    private static boolean sameSystem(SystemEntity current, SystemEntity requested) {
        if (current == null || requested == null) return current == requested;
        return current.id().equals(requested.id());
    }
}
