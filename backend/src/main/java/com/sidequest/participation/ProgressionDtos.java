package com.sidequest.participation;

import java.math.*;
import java.time.Instant;
import java.util.List;
import com.sidequest.reward.XpTransactionRow;
import java.util.UUID;

public final class ProgressionDtos {
    private ProgressionDtos() { }
    public record Profile(String username, Instant createdAt, long totalXp, long level, long xpIntoLevel,
            long xpRemaining, BigDecimal progressPercent, long joinedQuests, long activeQuests,
            long completedQuests, long createdQuests, long unlockedAchievementCount) { }
    public record Achievement(String id, String name, String description, long progress, long target,
            boolean unlocked, Instant unlockedAt) { }
    public record AchievementCatalog(List<Achievement> achievements) { }
    public record XpTransaction(UUID id, UUID participationId, UUID questId, int amount, Instant awardedAt,
            String questTitle, String category, String difficulty, String questStatus) { }
    public record XpPage(List<XpTransaction> content, int page, int size, long totalElements,
            int totalPages, boolean hasNext, boolean hasPrevious) { }

    static Profile profile(ProgressionReadRepository.Summary s, long unlocked) {
        if (s.totalXp() < 0 || s.integrityErrors() != 0) throw ParticipationFailure.integrity();
        long into = s.totalXp() % 500;
        return new Profile(s.username(), s.createdAt(), s.totalXp(), s.totalXp() / 500 + 1, into,
                500 - into, BigDecimal.valueOf(into).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(500), 2, RoundingMode.HALF_UP),
                s.joined(), s.active(), s.completed(), s.created(), unlocked);
    }
}
