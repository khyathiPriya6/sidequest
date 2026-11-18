package com.sidequest.quest;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<Quest, UUID> {
    Optional<Quest> findByIdAndCreator_Id(UUID id, UUID creatorId);

    Optional<Quest> findByIdAndStatus(UUID id, QuestStatus status);

    Page<Quest> findAllByStatus(QuestStatus status, Pageable pageable);

    Page<Quest> findAllByCreator_Id(UUID creatorId, Pageable pageable);

    Page<Quest> findAllByCreator_IdAndStatus(UUID creatorId, QuestStatus status, Pageable pageable);

    @Query("select quest.version from Quest quest where quest.id = :id and quest.creator.id = :creatorId")
    Optional<Long> findVersionByIdAndCreator_Id(
            @Param("id") UUID id, @Param("creatorId") UUID creatorId);
}
