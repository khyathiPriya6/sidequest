package com.sidequest.quest;

import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestRepository extends JpaRepository<Quest, UUID>, QuestRepositoryCustom {
    // Future join/archive transactions must acquire this same row before eligibility decisions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quest q where q.id = :id")
    Optional<Quest> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Quest q where q.id = :id and q.creator.id = :creatorId")
    Optional<Quest> findByIdAndCreatorForUpdate(@Param("id") UUID id, @Param("creatorId") UUID creatorId);

    Optional<Quest> findByIdAndCreator_Id(UUID id, UUID creatorId);

    Optional<Quest> findByIdAndStatus(UUID id, QuestStatus status);

    Page<Quest> findAllByCreator_Id(UUID creatorId, Pageable pageable);

    Page<Quest> findAllByCreator_IdAndStatus(UUID creatorId, QuestStatus status, Pageable pageable);

    @Query("select quest.version from Quest quest where quest.id = :id and quest.creator.id = :creatorId")
    Optional<Long> findVersionByIdAndCreator_Id(
            @Param("id") UUID id, @Param("creatorId") UUID creatorId);
}
