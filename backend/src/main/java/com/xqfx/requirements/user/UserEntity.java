package com.xqfx.requirements.user;

import com.xqfx.requirements.dictionary.DictionaryItemEntity;
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

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "users")
public class UserEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long recordVersion;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String displayName;

    @ManyToOne
    @JoinColumn(name = "department_id")
    private DictionaryItemEntity department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role = UserRole.USER;

    @Column(nullable = false)
    private boolean disabled = false;

    @Column(nullable = false)
    private boolean mustChangePassword = true;

    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected UserEntity() {
    }

    UserEntity(String username, String passwordHash, String displayName,
               DictionaryItemEntity department, UserRole role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.department = department;
        this.role = role == null ? UserRole.USER : role;
    }

    public Long id() {
        return id;
    }

    public String username() {
        return username;
    }

    String passwordHash() {
        return passwordHash;
    }

    public String displayName() {
        return displayName;
    }

    public DictionaryItemEntity department() {
        return department;
    }

    public UserRole role() {
        return role;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isHandler() {
        return role == UserRole.HANDLER || role == UserRole.ADMIN;
    }

    public boolean canManageSystems() {
        return isHandler();
    }

    public boolean canReceiveNotifications() {
        return !disabled;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    void updateProfile(String displayName, DictionaryItemEntity department, UserRole role) {
        this.displayName = displayName;
        this.department = department;
        this.role = role == null ? UserRole.USER : role;
    }

    void updateDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    /** 管理员重置密码：下次登录必须改密。 */
    void resetPassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        clearLoginFailures();
    }

    /** 用户自行修改密码。 */
    void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        clearLoginFailures();
    }

    boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(ZONE));
    }

    LocalDateTime lockedUntil() {
        return lockedUntil;
    }

    void recordLoginFailure() {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = LocalDateTime.now(ZONE).plusMinutes(LOCK_MINUTES);
            this.failedLoginAttempts = 0;
        }
    }

    void clearLoginFailures() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    @PrePersist
    void setInitialTimestamps() {
        var now = LocalDateTime.now(ZONE);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        this.updatedAt = LocalDateTime.now(ZONE);
    }
}
