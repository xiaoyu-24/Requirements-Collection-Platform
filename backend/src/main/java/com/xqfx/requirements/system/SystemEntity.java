package com.xqfx.requirements.system;

import com.xqfx.requirements.user.UserEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "systems")
public class SystemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long recordVersion;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, length = 100)
    private String activeNameKey;

    @Column(nullable = false, length = 50)
    private String ownerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private UserEntity ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemStatus status = SystemStatus.ACTIVE;

    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime deletedAt;

    @ElementCollection
    @CollectionTable(name = "system_collaborators", joinColumns = @JoinColumn(name = "system_id"))
    @Column(name = "collaborator_name", nullable = false, length = 50)
    private List<String> collaborators = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "system_collaborator_users",
            joinColumns = @JoinColumn(name = "system_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<UserEntity> collaboratorUsers = new LinkedHashSet<>();

    protected SystemEntity() {
    }

    public SystemEntity(SystemProfile profile) {
        this.name = profile.name();
        this.activeNameKey = normalizedName(profile.name());
        this.ownerName = profile.ownerName();
        this.collaborators = new ArrayList<>(profile.collaborators());
    }

    public SystemEntity(String name, UserEntity ownerUser, Collection<UserEntity> collaboratorUsers) {
        this.name = name.trim();
        this.activeNameKey = normalizedName(name);
        applyAccountBindings(ownerUser, collaboratorUsers);
    }

    void update(SystemProfile profile) {
        this.name = profile.name();
        this.activeNameKey = normalizedName(profile.name());
        this.ownerName = profile.ownerName();
        this.collaborators = new ArrayList<>(profile.collaborators());
    }

    void update(String name, UserEntity ownerUser, Collection<UserEntity> collaboratorUsers) {
        this.name = name.trim();
        this.activeNameKey = normalizedName(name);
        applyAccountBindings(ownerUser, collaboratorUsers);
    }

    public Long id() {
        return id;
    }

    long recordVersion() {
        return recordVersion;
    }

    public String name() {
        return name;
    }

    public String ownerName() {
        return ownerName;
    }

    public UserEntity ownerUser() {
        return ownerUser;
    }

    public List<String> collaborators() {
        return List.copyOf(collaborators);
    }

    public Set<UserEntity> collaboratorUsers() {
        return Set.copyOf(collaboratorUsers);
    }

    SystemStatus status() {
        return status;
    }

    public boolean isActive() {
        return status == SystemStatus.ACTIVE;
    }

    void updateStatus(SystemStatus status) {
        this.status = status;
    }

    void delete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.activeNameKey = null;
    }

    public static String normalizedName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private void applyAccountBindings(UserEntity ownerUser, Collection<UserEntity> collaboratorUsers) {
        this.ownerUser = ownerUser;
        this.ownerName = ownerUser.displayName();
        this.collaboratorUsers = new LinkedHashSet<>(collaboratorUsers);
        this.collaborators = collaboratorUsers.stream().map(UserEntity::displayName).toList();
    }
}
