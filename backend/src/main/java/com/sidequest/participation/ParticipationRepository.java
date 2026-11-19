package com.sidequest.participation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParticipationRepository extends JpaRepository<Participation, UUID> {
    Optional<Participation> findByUser_IdAndQuest_Id(UUID userId, UUID questId);

    Optional<Participation> findByIdAndUser_Id(UUID id, UUID userId);

    Page<Participation> findAllByUser_Id(UUID userId, Pageable pageable);

    Page<Participation> findAllByUser_IdAndStatus(UUID userId, ParticipationStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.id = :id and p.user.id = :userId")
    Optional<Participation> findByIdAndUserForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participation p where p.user.id = :userId and p.quest.id = :questId")
    Optional<Participation> findByUserAndQuestForUpdate(@Param("userId") UUID userId,
                                                         @Param("questId") UUID questId);
}
