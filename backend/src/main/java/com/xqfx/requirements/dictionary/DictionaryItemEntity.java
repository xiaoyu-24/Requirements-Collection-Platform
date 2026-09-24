package com.xqfx.requirements.dictionary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "dictionary_items")
public class DictionaryItemEntity {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long recordVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DictionaryCategory category;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private boolean disabled = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected DictionaryItemEntity() {
    }

    DictionaryItemEntity(DictionaryCategory category, String name) {
        this.category = category;
        this.name = name;
    }

    public Long id() {
        return id;
    }

    public long recordVersion() {
        return recordVersion;
    }

    public DictionaryCategory category() {
        return category;
    }

    public String name() {
        return name;
    }

    public boolean isDisabled() {
        return disabled;
    }

    void rename(String name) {
        this.name = name;
    }

    void updateDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    @PrePersist
    void setInitialTimestamps() {
        var now = LocalDateTime.now(ZONE);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = LocalDateTime.now(ZONE);
    }
}
