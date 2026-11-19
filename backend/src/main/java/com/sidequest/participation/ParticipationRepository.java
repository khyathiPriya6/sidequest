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
    String VIEW = "select new com.sidequest.participation.ParticipationRow(p.id, q.id, p.status, p.rewardXp, "
            + "p.joinedAt, p.completedAt, x.amount, x.awardedAt, q.title, q.description, q.category, q.difficulty, q.status) "
            + "from Participation p join p.quest q left join XpLedger x on x.participation = p ";

    @Query(value = VIEW + "where p.user.id = :userId and (:status is null or p.status = :status) "
            + "and (:questId is null or q.id = :questId)",
            countQuery = "select count(p) from Participation p where p.user.id = :userId "
            + "and (:status is null or p.status = :status) and (:questId is null or p.quest.id = :questId)")
    Page<ParticipationRow> history(@Param("userId") UUID userId, @Param("status") ParticipationStatus status,
            @Param("questId") UUID questId, Pageable pageable);

    @Query(VIEW + "where p.id = :id and p.user.id = :userId")
    Optional<ParticipationRow> detail(@Param("id") UUID id, @Param("userId") UUID userId);

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
