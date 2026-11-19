package com.sidequest.reward;

import com.sidequest.participation.Participation;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "xp_ledger")
public class XpLedger {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id", nullable = false, unique = true, updatable = false)
    private Participation participation;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    protected XpLedger() { }

    public static XpLedger award(Participation participation, Instant awardedAt) {
        Objects.requireNonNull(participation, "participation is required");
        Objects.requireNonNull(awardedAt, "awardedAt is required");
        int reward = participation.getRewardXp();
        if (participation.getStatus() != com.sidequest.participation.ParticipationStatus.COMPLETED) {
            throw new IllegalArgumentException("XP can only be awarded for a completed participation");
        }
        if (participation.getCompletedAt() == null || awardedAt.isBefore(participation.getCompletedAt())) {
            throw new IllegalArgumentException("Award timestamp must not precede completion");
        }
        XpLedger ledger = new XpLedger();
        ledger.id = UUID.randomUUID();
        ledger.participation = participation;
        ledger.amount = reward;
        ledger.awardedAt = awardedAt;
        return ledger;
    }

    @PrePersist
    void validateBeforePersist() {
        if (participation == null) throw new IllegalStateException("Participation is required");
        if (amount != participation.getRewardXp()) {
            throw new IllegalStateException("Ledger amount must equal the participation reward snapshot");
        }
        if (amount < 1 || amount > 1000) throw new IllegalStateException("Invalid ledger award");
        if (awardedAt == null) throw new IllegalStateException("awardedAt is required");
        if (participation.getCompletedAt() == null || awardedAt.isBefore(participation.getCompletedAt())) {
            throw new IllegalStateException("Invalid award timestamp");
        }
    }

    public UUID getId() { return id; }
    public UUID getParticipationId() { return participation.getId(); }
    public int getAmount() { return amount; }
    public Instant getAwardedAt() { return awardedAt; }
}
