package com.sidequest.participation;

import com.sidequest.reward.XpLedgerRepository;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerProfileService {
    private final ProgressionReadRepository progression;
    private final com.sidequest.reward.XpLedgerRepository ledger;
    private final StreakService streaks;
    public PlayerProfileService(ProgressionReadRepository progression, XpLedgerRepository ledger, StreakService streaks) {
        this.progression=progression; this.ledger=ledger; this.streaks=streaks;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ProgressionDtos.Profile profile(UUID userId) {
        var summary=progression.summary(userId);
        var achievements=AchievementCatalog.build(summary,progression.events(userId),streaks.history(userId));
        long unlocked=achievements.stream().filter(ProgressionDtos.Achievement::unlocked).count();
        return ProgressionDtos.profile(summary,unlocked);
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ProgressionDtos.AchievementCatalog achievements(UUID userId) {
        var summary=progression.summary(userId);
        return new ProgressionDtos.AchievementCatalog(AchievementCatalog.build(summary,progression.events(userId),streaks.history(userId)));
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public ProgressionDtos.XpPage transactions(UUID userId,int page,int size) {
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE) throw ParticipationFailure.badRequest("invalid_pagination");
        var pageable=PageRequest.of(page,size, Sort.by(Sort.Order.desc("awardedAt"),Sort.Order.desc("id")));
        var result=ledger.findOwnerTransactions(userId,pageable);
        return new ProgressionDtos.XpPage(result.getContent().stream().map(row -> {
            if(row.amount()<1||row.amount()>1000||row.amount()!=row.rewardXp()||row.completedAt()==null||row.awardedAt().isBefore(row.completedAt()))
                throw ParticipationFailure.integrity();
            return new ProgressionDtos.XpTransaction(row.id(),row.participationId(),row.questId(),row.amount(),row.awardedAt(),
                    row.questTitle(),row.category(),row.difficulty(),row.questStatus());
        }).toList(), result.getNumber(),result.getSize(),result.getTotalElements(),result.getTotalPages(),result.hasNext(),result.hasPrevious());
    }
}
