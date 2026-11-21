package com.sidequest.participation;

import com.sidequest.identity.UserRepository;
import com.sidequest.quest.*;
import com.sidequest.reward.XpLedgerRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipationService {
    private final ParticipationRepository participations;
    private final QuestRepository quests;
    private final UserRepository users;
    private final XpLedgerRepository ledger;
    private final java.time.Clock clock;

    ParticipationService(ParticipationRepository participations, QuestRepository quests,
            UserRepository users, XpLedgerRepository ledger, java.time.Clock clock) {
        this.participations = participations;
        this.quests = quests;
        this.users = users;
        this.ledger = ledger;
        this.clock = clock;
    }

    @Transactional
    public JoinResult join(UUID userId, UUID questId) {
        Quest quest = quests.findByIdForUpdate(questId).orElseThrow(ParticipationFailure::notFound);
        var existing = participations.findByUser_IdAndQuest_Id(userId, questId);
        if (existing.isPresent()) return new JoinResult(view(existing.get(), quest), false);
        if (quest.getStatus() != QuestStatus.PUBLISHED) {
            if (quest.getCreatorId().equals(userId)) {
                throw new ParticipationFailure(HttpStatus.CONFLICT, "quest_not_joinable", "The quest is not open for enrollment.");
            }
            throw ParticipationFailure.notFound();
        }
        if (quest.getCreatorId().equals(userId)) {
            throw new ParticipationFailure(HttpStatus.FORBIDDEN, "self_enrollment_forbidden", "You cannot join a quest you created.");
        }
        var user = users.findById(userId).orElseThrow(ParticipationFailure::notFound);
        Participation participation = participations.saveAndFlush(Participation.join(user, quest));
        return new JoinResult(view(participation, quest), true);
    }

    @Transactional
    public ParticipationDtos.View complete(UUID userId, UUID id) {
        Participation p = participations.findByIdAndUserForUpdate(id, userId)
                .orElseThrow(ParticipationFailure::notFound);
        var award = ledger.findByParticipationId(id).orElse(null);
        if (p.getStatus() == ParticipationStatus.COMPLETED) {
            ParticipationDtos.validateAward(p.getStatus(), p.getRewardXp(), p.getCompletedAt(),
                    award == null ? null : award.getAmount(), award == null ? null : award.getAwardedAt());
        } else {
            if (award != null) throw ParticipationFailure.integrity();
            var completedAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            if (completedAt.isBefore(p.getJoinedAt())) throw ParticipationFailure.integrity();
            p.complete(completedAt);
            participations.flush();
            // REQUIRED propagation: status and ledger either both commit or both roll back.
            award = ledger.save(com.sidequest.reward.XpLedger.award(p, completedAt));
        }
        Quest quest = quests.findById(p.getQuestId()).orElseThrow(ParticipationFailure::integrity);
        return ParticipationDtos.from(p, quest, award);
    }

    @Transactional(readOnly = true)
    public ParticipationDtos.HistoryPage history(UUID userId, ParticipationStatus status, UUID questId, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw ParticipationFailure.badRequest("invalid_pagination");
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Order.desc("joinedAt"),
                        org.springframework.data.domain.Sort.Order.desc("id")));
        return ParticipationDtos.HistoryPage.from(participations.history(userId, status, questId, pageable));
    }

    @Transactional(readOnly = true)
    public ParticipationDtos.View detail(UUID userId, UUID id) {
        return participations.detail(id, userId).orElseThrow(ParticipationFailure::notFound).view();
    }

    private ParticipationDtos.View view(Participation participation, Quest quest) {
        return ParticipationDtos.from(participation, quest,
                ledger.findByParticipationId(participation.getId()).orElse(null));
    }

    public record JoinResult(ParticipationDtos.View body, boolean created) { }
}
