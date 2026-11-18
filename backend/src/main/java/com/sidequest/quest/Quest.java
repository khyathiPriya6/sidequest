package com.sidequest.quest;

import com.sidequest.identity.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "quests")
public class Quest {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private User creator;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 3000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private QuestCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private QuestDifficulty difficulty;

    @Column(name = "reward_xp")
    private Integer rewardXp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private long version;

    protected Quest() { }

    public static Quest create(
            User creator,
            String title,
            String description,
            QuestCategory category,
            QuestDifficulty difficulty,
            Integer rewardXp) {
        Quest quest = new Quest();
        quest.id = UUID.randomUUID();
        quest.creator = Objects.requireNonNull(creator, "creator is required");
        quest.title = normalizeTitle(title);
        quest.description = normalizeDescription(description);
        quest.category = category;
        quest.difficulty = difficulty;
        quest.rewardXp = validateRewardXp(rewardXp);
        quest.status = QuestStatus.DRAFT;
        return quest;
    }

    void updateDraft(
            String title,
            String description,
            QuestCategory category,
            QuestDifficulty difficulty,
            Integer rewardXp) {
        if (status != QuestStatus.DRAFT) {
            throw new IllegalStateException("Only a draft quest can be edited");
        }
        this.title = normalizeTitle(title);
        this.description = normalizeDescription(description);
        this.category = category;
        this.difficulty = difficulty;
        this.rewardXp = validateRewardXp(rewardXp);
    }

    void publish() {
        if (status != QuestStatus.DRAFT) {
            throw new IllegalStateException("Only a draft quest can be published");
        }
        if (description == null || category == null || difficulty == null || rewardXp == null) {
            throw new IllegalStateException("A quest must satisfy publication requirements");
        }
        status = QuestStatus.PUBLISHED;
        publishedAt = Instant.now();
    }

    void archive() {
        if (status != QuestStatus.DRAFT && status != QuestStatus.PUBLISHED) {
            throw new IllegalStateException("Only a draft or published quest can be archived");
        }
        status = QuestStatus.ARCHIVED;
        archivedAt = Instant.now();
    }

    private static String normalizeTitle(String title) {
        String normalized = Objects.requireNonNull(title, "title is required").strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 3 || length > 120) {
            throw new IllegalArgumentException("Title must contain 3 to 120 Unicode code points");
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 10 || length > 3000) {
            throw new IllegalArgumentException("Description must contain 10 to 3000 Unicode code points");
        }
        return normalized;
    }

    private static Integer validateRewardXp(Integer rewardXp) {
        if (rewardXp != null && (rewardXp < 1 || rewardXp > 1000)) {
            throw new IllegalArgumentException("XP reward must be between 1 and 1000");
        }
        return rewardXp;
    }

    @PrePersist
    void created() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCreatorId() { return creator.getId(); }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public QuestCategory getCategory() { return category; }
    public QuestDifficulty getDifficulty() { return difficulty; }
    public Integer getRewardXp() { return rewardXp; }
    public QuestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version; }
}
