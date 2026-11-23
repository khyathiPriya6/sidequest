package com.sidequest.moderation;

import com.sidequest.quest.Quest;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Internal review record. Never directly return this JPA entity from an API. */
@Entity
@Table(name = "quest_moderation")
public class QuestModeration {
    @Id
    @Column(name = "quest_id", nullable = false)
    private UUID questId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "quest_id", nullable = false, updatable = false)
    private Quest quest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ModerationState state;

    @Column(name = "policy_version", nullable = false, length = 32)
    private String policyVersion;

    @Column(name = "reason_code", length = 40)
    private String reasonCode;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    private long version;

    protected QuestModeration() { }

    public static QuestModeration unreviewed(Quest quest) {
        QuestModeration record = new QuestModeration();
        record.quest = Objects.requireNonNull(quest);
        record.questId = quest.getId();
        record.state = ModerationState.UNREVIEWED;
        record.policyVersion = "m3-v1";
        return record;
    }

    /** Any draft revision invalidates previously submitted or approved content. */
    public void invalidateReview() {
        if (state == ModerationState.UNREVIEWED) return;
        if (state != ModerationState.PENDING_REVIEW && state != ModerationState.APPROVED
                && state != ModerationState.REJECTED) {
            throw new IllegalStateException("Cannot edit a hidden moderation record");
        }
        state = ModerationState.UNREVIEWED;
        reasonCode = null;
        submittedAt = null;
        reviewedAt = null;
    }

    /** Deterministic screening prioritizes review; it never grants approval. */
    public void submit(QuestSafetyScreen.Result screening, Instant now) {
        Objects.requireNonNull(screening);
        Objects.requireNonNull(now);
        if (state == ModerationState.PENDING_REVIEW) return;
        if (state != ModerationState.UNREVIEWED && state != ModerationState.REJECTED) {
            throw new IllegalStateException("Quest cannot be submitted from this review state");
        }
        state = ModerationState.PENDING_REVIEW;
        submittedAt = now;
        reviewedAt = null;
        reasonCode = screening.reasons().isEmpty() ? null : screening.reasons().get(0).name();
    }

    /** For a later authenticated moderator workflow, not called by a public endpoint. */
    public void approve(Instant when) {
        Objects.requireNonNull(when);
        if (state != ModerationState.PENDING_REVIEW && state != ModerationState.UNDER_REVIEW) {
            throw new IllegalStateException("Only pending reviews can be approved");
        }
        state = ModerationState.APPROVED;
        reviewedAt = when;
        reasonCode = null;
    }

    public void appeal(Instant when) {
        Objects.requireNonNull(when);
        if (state != ModerationState.REJECTED) {
            throw new IllegalStateException("Only rejected drafts can be appealed");
        }
        state = ModerationState.UNDER_REVIEW;
        submittedAt = when;
        reviewedAt = null;
    }

    /** Human rejection leaves the Quest private; a creator may edit and resubmit. */
    public void reject(Instant when, String code) {
        Objects.requireNonNull(when);
        Objects.requireNonNull(code);
        if (state != ModerationState.PENDING_REVIEW && state != ModerationState.UNDER_REVIEW) {
            throw new IllegalStateException("Only a pending review can be rejected");
        }
        if (code.isBlank() || code.length() > 40) throw new IllegalArgumentException("Invalid safety reason");
        state = ModerationState.REJECTED;
        reviewedAt = when;
        reasonCode = code;
    }

    /** Staff-only legacy review; hides a pre-existing published Quest without deleting XP. */
    public void rejectLegacy(Instant when, String code) {
        Objects.requireNonNull(when);
        Objects.requireNonNull(code);
        if (state != ModerationState.PENDING_REVIEW) {
            throw new IllegalStateException("Only pending legacy reviews may be rejected");
        }
        if (code.isBlank() || code.length() > 40) {
            throw new IllegalArgumentException("Invalid safety reason");
        }
        state = ModerationState.HIDDEN;
        reviewedAt = when;
        reasonCode = code;
    }

    /** Internal action called only by server-authorized moderation operations. */
    public void hide(Instant when, String code) {
        Objects.requireNonNull(when);
        Objects.requireNonNull(code);
        if (state != ModerationState.APPROVED && state != ModerationState.UNDER_REVIEW) {
            throw new IllegalStateException("Only reviewed content can be hidden");
        }
        if (code.isBlank() || code.length() > 40) throw new IllegalArgumentException("Invalid safety reason");
        state = ModerationState.HIDDEN;
        reviewedAt = when;
        reasonCode = code;
    }

    public boolean approved() { return state == ModerationState.APPROVED; }

    public UUID getQuestId() { return questId; }
    public ModerationState getState() { return state; }
    public String getPolicyVersion() { return policyVersion; }
    public String getReasonCode() { return reasonCode; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public long getVersion() { return version; }
}
