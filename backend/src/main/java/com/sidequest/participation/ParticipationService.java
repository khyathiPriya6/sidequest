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

    ParticipationService(ParticipationRepository participations, QuestRepository quests,
            UserRepository users, XpLedgerRepository ledger) {
        this.participations = participations;
        this.quests = quests;
        this.users = users;
        this.ledger = ledger;
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
