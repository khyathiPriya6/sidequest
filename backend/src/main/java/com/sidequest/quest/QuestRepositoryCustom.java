package com.sidequest.quest;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuestRepositoryCustom {
    Page<Quest> searchPublished(
            String query, QuestCategory category, QuestDifficulty difficulty, Pageable pageable);
}
