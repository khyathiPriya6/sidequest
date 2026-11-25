package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class StreakIT extends GamificationTestSupport {
    StreakService at(String now) { return new StreakService(jdbc, Clock.fixed(Instant.parse(now), ZoneOffset.UTC)); }

    @Test void distinctDaysContinueAcrossYearAndExpireOnlyAfterMissedDay() throws Exception {
        var creator=register();var player=register();
        assertThat(at("2027-01-01T12:00:00Z").streak(player.id())).isEqualTo(new StreakService.Streak(0,0,null,"UTC"));
        for(String time:List.of("2026-12-30T23:59:59Z","2026-12-31T00:00:00Z","2026-12-31T23:59:59Z","2027-01-01T00:00:00Z"))
            award(creator,player,Instant.parse(time),75);
        var today=at("2027-01-01T12:00:00Z").streak(player.id());
        assertThat(today.currentStreak()).isEqualTo(3);
        assertThat(today.longestStreak()).isEqualTo(3);
        assertThat(today.lastCompletionDate()).isEqualTo(LocalDate.parse("2027-01-01"));
        assertThat(at("2027-01-02T23:59:59Z").streak(player.id()).currentStreak()).isEqualTo(3);
        assertThat(at("2027-01-03T00:00:00Z").streak(player.id()).currentStreak()).isZero();
        award(creator,player,Instant.parse("2027-01-03T01:00:00Z"),75);
        var restarted=at("2027-01-03T02:00:00Z").streak(player.id());
        assertThat(restarted.currentStreak()).isEqualTo(1);
        assertThat(restarted.longestStreak()).isEqualTo(3);
        assertThat(at("2026-12-29T12:00:00Z").streak(player.id()).currentStreak()).isZero();
    }

    @Test void leapDayAndMonthBoundaryUseUtcNotJvmTimezone() throws Exception {
        var creator=register();var player=register();
        for(String time:List.of("2028-02-28T23:59:59Z","2028-02-29T00:00:00Z","2028-03-01T00:00:00Z"))
            award(creator,player,Instant.parse(time),75);
        assertThat(at("2028-03-01T00:00:00Z").streak(player.id()).currentStreak()).isEqualTo(3);
    }

    @Test void concurrentCompletionsAndReplayCountOneDayAndExactlyTwoAwards() throws Exception {
        var creator=register();var player=register();
        var q1=quest(creator,true);var q2=quest(creator,true);
        UUID p1=UUID.fromString(body(join(player,q1.id())).get("id").asText());
        UUID p2=UUID.fromString(body(join(player,q2.id())).get("id").asText());
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(3)) {
            var futures=List.of(p1,p2,p1).stream().map(p->pool.submit(()->{await(start);return service.complete(player.id(),p);})).toList();
            start.countDown();
            for(var future:futures) assertThat(future.get(10,TimeUnit.SECONDS)).isNotNull();
        }
        var result=new StreakService(jdbc,Clock.systemUTC()).streak(player.id());
        assertThat(result.currentStreak()).isEqualTo(1);
        assertThat(result.longestStreak()).isEqualTo(1);
        assertThat(body(request(player,"GET","/api/v1/me/profile",null)).get("totalXp").asLong()).isEqualTo(350);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_ledger x JOIN participations p ON p.id=x.participation_id WHERE p.user_id=?",Long.class,player.id())).isEqualTo(2);
        assertThat(request(player,"POST","/api/v1/participations/"+p1+"/complete","{\"completedAt\":\"2020-01-01T00:00:00Z\"}").statusCode()).isEqualTo(400);
    }

    @Test void ownOnlyNoStoreAndNoTimestampMutation() throws Exception {
        var player=register();
        assertThat(send(HttpClient.newHttpClient(),"GET","/api/v1/me/streak",null,null,null).statusCode()).isEqualTo(401);
        var response=request(player,"GET","/api/v1/me/streak?userId="+UUID.randomUUID()+"&date=2099-01-01",null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        assertThat(body(response).get("currentStreak").asLong()).isZero();
        assertThat(body(response).get("lastCompletionDate").isNull()).isTrue();
        assertThat(request(player,"POST","/api/v1/me/streak","{}").statusCode()).isEqualTo(403);
    }
}
