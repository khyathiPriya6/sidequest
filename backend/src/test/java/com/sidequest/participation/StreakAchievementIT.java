package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StreakAchievementIT extends GamificationTestSupport {
    @Test void unlocksOnlyOnceAtFirstQualifyingDayAndPreservesAfterBreak() throws Exception {
        var creator=register();var player=register();
        Instant start=Instant.parse("2026-01-01T01:00:00Z");
        for(int day=0;day<7;day++) {
            award(creator,player,start.plusSeconds(day*86400L),75);
            award(creator,player,start.plusSeconds(day*86400L+60),75);
        }
        var history=new StreakService(jdbc,Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"),ZoneOffset.UTC)).history(player.id());
        assertThat(history.streak().currentStreak()).isZero();
        assertThat(history.streak().longestStreak()).isEqualTo(7);
        assertThat(history.firstThreeDays()).isEqualTo(start.plusSeconds(2*86400L));
        assertThat(history.firstSevenDays()).isEqualTo(start.plusSeconds(6*86400L));
        var first=body(request(player,"GET","/api/v1/me/achievements",null)).get("achievements");
        var second=body(request(player,"GET","/api/v1/me/achievements",null)).get("achievements");
        assertThat(second).isEqualTo(first);
        assertThat(first).hasSize(11);
        assertThat(first.get(9).get("id").asText()).isEqualTo("consistent_player");
        assertThat(first.get(9).get("unlockedAt").asText()).isEqualTo(start.plusSeconds(2*86400L).toString());
        assertThat(first.get(10).get("id").asText()).isEqualTo("week_warrior");
        assertThat(first.get(10).get("progress").asInt()).isEqualTo(7);
        assertThat(first.get(10).get("unlockedAt").asText()).isEqualTo(start.plusSeconds(6*86400L).toString());
        assertThat(body(request(player,"GET","/api/v1/me/profile",null)).get("totalXp").asLong()).isEqualTo(1050);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_ledger x JOIN participations p ON p.id=x.participation_id WHERE p.user_id=?",Long.class,player.id())).isEqualTo(14);
    }

    @Test void lockedProgressUsesLongestNotTotalActiveDaysAndDoesNotDuplicateExistingRules() throws Exception {
        var creator=register();var player=register();
        for(String date:List.of("2026-01-01","2026-01-02","2026-01-04","2026-01-06"))
            award(creator,player,Instant.parse(date+"T00:00:00Z"),125);
        var entries=body(request(player,"GET","/api/v1/me/achievements",null)).get("achievements");
        var ids=new HashSet<String>(); entries.forEach(a->ids.add(a.get("id").asText()));
        assertThat(ids).hasSize(11).contains("first_adventure","quest_explorer","quest_veteran","xp_rookie");
        for(int index:List.of(9,10)) {
            assertThat(entries.get(index).get("progress").asInt()).isEqualTo(2);
            assertThat(entries.get(index).get("unlocked").asBoolean()).isFalse();
            assertThat(entries.get(index).get("unlockedAt").isNull()).isTrue();
        }
        assertThat(entries.get(4).get("unlocked").asBoolean()).isTrue();
    }
}
