package com.sidequest.participation;

import com.sidequest.quest.*;
import java.time.Instant;
import java.util.UUID;

/** Scalar projection: page queries never initialize per-row entity associations. */
public record ParticipationRow(UUID id, UUID questId, ParticipationStatus status, int rewardXp,
        Instant joinedAt, Instant completedAt, Integer awardedXp, Instant awardedAt,
        String title, String description, QuestCategory category, QuestDifficulty difficulty, QuestStatus questStatus) {
    ParticipationDtos.View view() {
        ParticipationDtos.validateAward(status, rewardXp, completedAt, awardedXp, awardedAt);
        return new ParticipationDtos.View(id, questId, status, rewardXp, joinedAt, completedAt,
                awardedXp == null ? 0 : awardedXp,
                new ParticipationDtos.QuestSummary(title, description, category, difficulty, questStatus));
    }
}
