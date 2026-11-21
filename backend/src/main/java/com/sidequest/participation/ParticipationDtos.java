package com.sidequest.participation;

import com.sidequest.quest.*;
import com.sidequest.reward.XpLedger;
import java.time.Instant;
import java.util.UUID;

public final class ParticipationDtos {
    private ParticipationDtos() { }

    public record QuestSummary(String title, String description, QuestCategory category,
            QuestDifficulty difficulty, QuestStatus status) { }

    public record View(UUID id, UUID questId, ParticipationStatus status, int rewardXp,
            Instant joinedAt, Instant completedAt, int awardedXp, QuestSummary quest) { }

    public record HistoryPage(java.util.List<View> content, int page, int size, long totalElements,
            int totalPages, boolean hasNext, boolean hasPrevious) {
        static HistoryPage from(org.springframework.data.domain.Page<ParticipationRow> result) {
            return new HistoryPage(result.getContent().stream().map(ParticipationRow::view).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                    result.hasNext(), result.hasPrevious());
        }
    }

    static View from(Participation p, Quest q, XpLedger award) {
        validateAward(p.getStatus(), p.getRewardXp(), p.getCompletedAt(),
                award == null ? null : award.getAmount(), award == null ? null : award.getAwardedAt());
        return new View(p.getId(), q.getId(), p.getStatus(), p.getRewardXp(), p.getJoinedAt(),
                p.getCompletedAt(), award == null ? 0 : award.getAmount(),
                new QuestSummary(q.getTitle(), q.getDescription(), q.getCategory(), q.getDifficulty(), q.getStatus()));
    }

    static void validateAward(ParticipationStatus status, int reward, Instant completed,
            Integer amount, Instant awarded) {
        if (status == ParticipationStatus.IN_PROGRESS ? amount != null :
                amount == null || amount != reward || awarded == null || completed == null || awarded.isBefore(completed)) {
            throw ParticipationFailure.integrity();
        }
    }
}
