package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpClient;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class LeaderboardIT extends GamificationTestSupport {
    static final Instant NOW = Instant.parse("2026-02-02T12:00:00Z");
    LeaderboardService board() { return new LeaderboardService(jdbc, Clock.fixed(NOW, ZoneOffset.UTC)); }


    @Test void utcPeriodsTiesStablePaginationZeroAndFutureEvents() throws Exception {
        var creator=register(); var a=register(); var b=register(); var zero=register();
        award(creator,a,Instant.parse("2026-01-31T23:59:59.999999Z"),100);
        award(creator,a,Instant.parse("2026-02-01T00:00:00Z"),200);
        award(creator,a,Instant.parse("2026-02-02T00:00:00Z"),300);
        award(creator,b,NOW,600);
        award(creator,a,NOW.plusSeconds(1),1000);
        var service=board();
        assertThat(service.rank(a.id(),"all").player().earnedXp()).isEqualTo(600);
        assertThat(service.rank(a.id(),"monthly").player().earnedXp()).isEqualTo(500);
        assertThat(service.rank(a.id(),"weekly").player().earnedXp()).isEqualTo(300);
        assertThat(service.rank(a.id(),"all").player().rank()).isEqualTo(service.rank(b.id(),"all").player().rank());
        assertThat(service.rank(zero.id(),"all").player()).isNull();
        var rows=service.board("all",0,100).content();
        var tied=rows.stream().filter(e->e.earnedXp()==600).map(LeaderboardService.Entry::username).toList();
        assertThat(tied).isSorted();
        assertThat(service.board("all",0,1).content()).containsExactly(rows.getFirst());
        assertThat(service.board("all",1,1).content()).containsExactly(rows.get(1));
        assertThat(service.board("all",10000,20).content()).isEmpty();
    }

    @Test void excludesUnsafeReportedAndDisabledWithoutMutatingPrivateXp() throws Exception {
        var creator=register();var player=register();var reporter=register();
        UUID q=award(creator,player,NOW,175);
        assertThat(board().rank(player.id(),"all").player().earnedXp()).isEqualTo(175);
        jdbc.update("INSERT INTO quest_reports(id,quest_id,reporter_id,reason,created_at) VALUES(?,?,?,'OTHER',?)",UUID.randomUUID(),q,reporter.id(),Timestamp.from(NOW));
        assertThat(board().rank(player.id(),"all").player()).isNull();
        jdbc.update("UPDATE quest_reports SET resolution='DISMISSED', resolved_at=?, resolved_by=? WHERE quest_id=?",Timestamp.from(NOW),creator.id(),q);
        assertThat(board().rank(player.id(),"all").player()).isNotNull();
        jdbc.update("UPDATE quest_moderation SET state='HIDDEN' WHERE quest_id=?",q);
        assertThat(board().rank(player.id(),"all").player()).isNull();
        assertThat(body(request(player,"GET","/api/v1/me/profile",null)).get("totalXp").asLong()).isEqualTo(175);
        jdbc.update("UPDATE quest_moderation SET state='APPROVED' WHERE quest_id=?",q);
        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?",player.id());
        assertThat(board().rank(player.id(),"all").player()).isNull();
    }

    @Test void protectsRoutesBoundsInputAndExposesOnlySafeFields() throws Exception {
        var player=register();var guest=HttpClient.newHttpClient();
        for(String route:List.of("/api/v1/leaderboard","/api/v1/me/leaderboard-rank")) {
            assertThat(send(guest,"GET",route,null,null,null).statusCode()).isEqualTo(401);
            var result=request(player,"GET",route,null);
            assertThat(result.statusCode()).isEqualTo(200);
            assertThat(result.headers().firstValue("cache-control")).contains("no-store");
            assertThat(result.body()).doesNotContain("email","password","userId");
            assertThat(request(player,"GET",route+"?period=invalid",null).statusCode()).isEqualTo(400);
            assertThat(send(player.client(),"POST",route,"{}",null,null).statusCode()).isEqualTo(403);
        }
        for(String query:List.of("?page=-1","?size=0","?size=101","?page=2147483647&size=100","?page=x"))
            assertThat(request(player,"GET","/api/v1/leaderboard"+query,null).statusCode()).isEqualTo(400);
    }

    @Test void definesMondayAndMonthBoundariesAcrossYears() {
        assertThat(LeaderboardService.period("weekly",Instant.parse("2027-01-01T00:00:00Z")).start()).isEqualTo(Instant.parse("2026-12-28T00:00:00Z"));
        assertThat(LeaderboardService.period("monthly",Instant.parse("2027-01-01T00:00:00Z")).start()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        assertThat(LeaderboardService.period("weekly",Instant.parse("2026-02-01T23:59:59Z")).start()).isEqualTo(Instant.parse("2026-01-26T00:00:00Z"));
    }
}
