package com.sidequest.quest;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QuestDtos {
    private QuestDtos() { }

    public record CreateDraft(JsonNode title, JsonNode description, JsonNode category,
            JsonNode difficulty, JsonNode rewardXp) { }

    public record PatchDraft(JsonNode title, JsonNode description, JsonNode category,
            JsonNode difficulty, JsonNode rewardXp) { }

    public record QuestView(UUID id, UUID creatorId, String title, String description,
            QuestCategory category, QuestDifficulty difficulty, Integer rewardXp,
            QuestStatus status, Instant createdAt, Instant updatedAt,
            Instant publishedAt, Instant archivedAt) {
        static QuestView from(Quest quest) {
            return new QuestView(quest.getId(), quest.getCreatorId(), quest.getTitle(),
                    quest.getDescription(), quest.getCategory(), quest.getDifficulty(),
                    quest.getRewardXp(), quest.getStatus(), quest.getCreatedAt(),
                    quest.getUpdatedAt(), quest.getPublishedAt(), quest.getArchivedAt());
        }
    }

    public record QuestPage(List<QuestView> content, int page, int size, long totalElements,
            int totalPages, boolean hasNext, boolean hasPrevious) {
        static QuestPage from(org.springframework.data.domain.Page<Quest> result) {
            return new QuestPage(result.getContent().stream().map(QuestView::from).toList(),
                    result.getNumber(), result.getSize(), result.getTotalElements(),
                    result.getTotalPages(), result.hasNext(), result.hasPrevious());
        }
    }
}
