package com.sidequest.reward;

import java.time.Instant;
import java.util.UUID;

public record XpTransactionRow(UUID id, UUID participationId, UUID questId, int amount,
        int rewardXp, Instant completedAt, Instant awardedAt, String questTitle,
        String category, String difficulty, String questStatus) { }
