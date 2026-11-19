package com.sidequest.participation;

import com.sidequest.identity.User;
import com.sidequest.quest.Quest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "participations")
public class Participation {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quest_id", nullable = false, updatable = false)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipationStatus status;

    @Column(name = "reward_xp", nullable = false, updatable = false)
    private int rewardXp;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Participation() { }

    public static Participation join(User user, Quest quest) {
        Objects.requireNonNull(user, "user is required");
        Objects.requireNonNull(quest, "quest is required");
        if (quest.getRewardXp() == null) {
            throw new IllegalArgumentException("A published quest reward is required for participation");
        }
        if (quest.getRewardXp() < 1 || quest.getRewardXp() > 1000) {
            throw new IllegalArgumentException("Reward XP must be between 1 and 1000");
        }
        Participation participation = new Participation();
        participation.id = UUID.randomUUID();
        participation.user = user;
        participation.quest = quest;
        participation.status = ParticipationStatus.IN_PROGRESS;
        participation.rewardXp = quest.getRewardXp();
        participation.joinedAt = Instant.now();
        participation.completedAt = null;
        return participation;
    }

    public void complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, "completedAt is required");
        if (status == ParticipationStatus.COMPLETED) return;
        if (completedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("Completion timestamp cannot precede joining");
        }
        status = ParticipationStatus.COMPLETED;
        this.completedAt = completedAt;
    }

    @PrePersist
    void validateBeforePersist() {
        validateInvariants();
    }

    @PreUpdate
    void validateBeforeUpdate() {
        validateInvariants();
    }

    private void validateInvariants() {
        if (user == null || quest == null) throw new IllegalStateException("Participation ownership is required");
        if (rewardXp < 1 || rewardXp > 1000) throw new IllegalStateException("Invalid reward snapshot");
        if (joinedAt == null) throw new IllegalStateException("joinedAt is required");
        if (status == ParticipationStatus.IN_PROGRESS && completedAt != null) {
            throw new IllegalStateException("In-progress participation cannot have completedAt");
        }
        if (status == ParticipationStatus.COMPLETED) {
            if (completedAt == null) throw new IllegalStateException("Completed participation requires completedAt");
            if (completedAt.isBefore(joinedAt)) throw new IllegalStateException("Invalid completion timestamp");
        }
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return user.getId(); }
    public UUID getQuestId() { return quest.getId(); }
    public ParticipationStatus getStatus() { return status; }
    public int getRewardXp() { return rewardXp; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
