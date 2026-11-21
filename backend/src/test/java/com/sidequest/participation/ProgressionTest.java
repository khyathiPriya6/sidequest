package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProgressionTest {
    private ProgressionReadRepository.Summary summary(long xp,long completed,long categories,long hard) {
        return new ProgressionReadRepository.Summary("player",Instant.EPOCH,xp,completed,0,completed,0,categories,hard,0);
    }
    private ProgressionReadRepository.Events empty() {
        return new ProgressionReadRepository.Events(null,null,null,null,null,null,null,null,null);
    }
    @Test void computesLevelBoundariesWithoutIntegerOverflowOrDrift() {
        long[] xp={0,499,500,999,1000,Long.MAX_VALUE};
        long[] levels={1,1,2,2,3,Long.MAX_VALUE/500+1};
        for(int i=0;i<xp.length;i++) {
            var profile=ProgressionDtos.profile(summary(xp[i],0,0,0),0);
            assertThat(profile.level()).isEqualTo(levels[i]);
            assertThat(profile.xpIntoLevel()).isEqualTo(xp[i]%500);
            assertThat(profile.xpRemaining()).isEqualTo(500-xp[i]%500);
            assertThat(profile.progressPercent()).isEqualByComparingTo(java.math.BigDecimal.valueOf(xp[i]%500).multiply(java.math.BigDecimal.valueOf(100)).divide(java.math.BigDecimal.valueOf(500),2,java.math.RoundingMode.HALF_UP));
        }
    }
    @Test void exposesAllNineLockedAchievementsWithoutInventingTimestamps() {
        var entries=AchievementCatalog.build(summary(499,0,5,4),empty());
        assertThat(entries).extracting(ProgressionDtos.Achievement::id).containsExactly(
                "first_adventure","quest_explorer","quest_veteran","dedicated_adventurer","xp_rookie",
                "rising_hero","quest_master","all_rounder","challenge_seeker");
        assertThat(entries).allSatisfy(entry -> assertThat(entry.unlockedAt()).isNull());
        assertThat(entries.get(0).progress()).isZero();
        assertThat(entries.get(1).progress()).isZero();
        assertThat(entries.get(7).progress()).isEqualTo(5);
    }
    @Test void rejectsContradictoryDerivedUnlockData() {
        var events=new ProgressionReadRepository.Events(Instant.EPOCH,null,null,null,null,null,null,null,null);
        assertThatThrownBy(() -> AchievementCatalog.build(summary(0,0,0,0),events))
                .isInstanceOf(ParticipationFailure.class);
        assertThatThrownBy(() -> ProgressionDtos.profile(summary(-1,0,0,0),0))
                .isInstanceOf(ParticipationFailure.class);
    }
}
