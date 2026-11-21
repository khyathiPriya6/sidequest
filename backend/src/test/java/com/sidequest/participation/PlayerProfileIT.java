package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpClient;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlayerProfileIT extends ParticipationApiSupport {
    private static final List<String> CATEGORIES=List.of("ADVENTURE","CREATIVITY","FITNESS","LEARNING","FOOD","SOCIAL");
    private void seedCompleteHistory(Session creator,Session player,int count,int reward) {
        Instant start=Instant.parse("2026-01-01T00:00:00Z");
        for(int i=0;i<count;i++) {
            UUID quest=UUID.randomUUID(), participation=UUID.randomUUID();
            String category=CATEGORIES.get(i%CATEGORIES.size());
            String difficulty=i<5?"HARD":"EASY";
            Instant created=start.plusSeconds(i*86400L-10), joined=start.plusSeconds(i*86400L), completed=joined.plusSeconds(1), awarded=joined.plusSeconds(2);
            jdbc.update("""
                INSERT INTO quests(id,creator_id,title,description,category,difficulty,reward_xp,status,created_at,updated_at,published_at,archived_at,version)
                VALUES(?,?,?,? ,?,?,?,'ARCHIVED',?,?,?,?,0)
                """,quest,creator.id(),"historical quest "+i,"A complete description for this quest.",category,difficulty,reward,Timestamp.from(created),Timestamp.from(created),Timestamp.from(created),Timestamp.from(created.plusSeconds(1)));
            jdbc.update("""
                INSERT INTO participations(id,user_id,quest_id,status,reward_xp,joined_at,completed_at)
                VALUES(?,?,?,'COMPLETED',?,?,?)""",participation,player.id(),quest,reward,Timestamp.from(joined),Timestamp.from(completed));
            jdbc.update("INSERT INTO xp_ledger(id,participation_id,amount,awarded_at) VALUES(?,?,?,?)",
                    UUID.randomUUID(),participation,reward,Timestamp.from(awarded));
        }
    }

    @Test void returnsZeroProfileAndLockedAchievementsForNewPlayers() throws Exception {
        var player=register();
        var response=request(player,"GET","/api/v1/me/profile",null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        var profile=body(response);
        assertThat(profile.get("username").asText()).isNotBlank();
        assertThat(profile.get("createdAt").asText()).endsWith("Z");
        assertThat(profile.get("totalXp").asLong()).isZero();
        assertThat(profile.get("level").asLong()).isEqualTo(1);
        assertThat(profile.get("xpRemaining").asLong()).isEqualTo(500);
        assertThat(profile.get("progressPercent").asDouble()).isZero();
        assertThat(profile.get("joinedQuests").asLong()).isZero();
        assertThat(profile.get("createdQuests").asLong()).isZero();
        assertThat(profile.get("unlockedAchievementCount").asLong()).isZero();
        var catalog=body(request(player,"GET","/api/v1/me/achievements",null));
        assertThat(catalog.get("achievements")).hasSize(9);
        assertThat(catalog.get("achievements")).allSatisfy(a->{assertThat(a.get("unlocked").asBoolean()).isFalse();assertThat(a.get("unlockedAt").isNull()).isTrue();});
        assertThat(body(request(player,"GET","/api/v1/me/xp-transactions",null)).get("totalElements").asLong()).isZero();
    }

    @Test void aggregatesArchivedHistoryAndDerivesEveryAchievementTimestamp() throws Exception {
        var creator=register();var player=register();var stranger=register();
        seedCompleteHistory(creator,player,25,200);
        var creatorProfile=body(request(creator,"GET","/api/v1/me/profile",null));
        assertThat(creatorProfile.get("createdQuests").asLong()).isEqualTo(25);
        assertThat(creatorProfile.get("totalXp").asLong()).isZero();
        var profile=body(request(player,"GET","/api/v1/me/profile",null));
        assertThat(profile.get("totalXp").asLong()).isEqualTo(5000);
        assertThat(profile.get("level").asLong()).isEqualTo(11);
        assertThat(profile.get("xpIntoLevel").asLong()).isZero();
        assertThat(profile.get("xpRemaining").asLong()).isEqualTo(500);
        assertThat(profile.get("completedQuests").asLong()).isEqualTo(25);
        assertThat(profile.get("activeQuests").asLong()).isZero();
        assertThat(profile.get("joinedQuests").asLong()).isEqualTo(25);
        assertThat(profile.get("createdQuests").asLong()).isZero();
        assertThat(profile.get("unlockedAchievementCount").asLong()).isEqualTo(9);
        var catalog=body(request(player,"GET","/api/v1/me/achievements",null)).get("achievements");
        assertThat(catalog).hasSize(9);
        assertThat(catalog).allSatisfy(a->{assertThat(a.get("unlocked").asBoolean()).isTrue();assertThat(a.get("unlockedAt").asText()).endsWith("Z");});
        assertThat(catalog.get(0).get("unlockedAt").asText()).isEqualTo("2026-01-01T00:00:01Z");
        assertThat(catalog.get(1).get("unlockedAt").asText()).isEqualTo("2026-01-05T00:00:01Z");
        assertThat(catalog.get(3).get("unlockedAt").asText()).isEqualTo("2026-01-25T00:00:01Z");
        assertThat(catalog.get(4).get("unlockedAt").asText()).isEqualTo("2026-01-03T00:00:02Z");
        assertThat(catalog.get(6).get("unlockedAt").asText()).isEqualTo("2026-01-25T00:00:02Z");
        assertThat(catalog.get(7).get("unlockedAt").asText()).isEqualTo("2026-01-06T00:00:01Z");
        assertThat(catalog.get(8).get("unlockedAt").asText()).isEqualTo("2026-01-05T00:00:01Z");
        var firstPage=body(request(player,"GET","/api/v1/me/xp-transactions?size=20",null));
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(25);
        assertThat(firstPage.get("content")).hasSize(20);
        assertThat(firstPage.get("content").get(0).get("questStatus").asText()).isEqualTo("ARCHIVED");
        assertThat(firstPage.get("content").get(0).get("amount").asInt()).isEqualTo(200);
        var next=body(request(player,"GET","/api/v1/me/xp-transactions?page=1&size=20",null));
        assertThat(next.get("content")).hasSize(5);
        assertThat(body(request(stranger,"GET","/api/v1/me/xp-transactions",null)).get("totalElements").asLong()).isZero();
    }

    @Test void protectsProfileRoutesAndBoundsTransactionPagination() throws Exception {
        var player=register(); var guest=HttpClient.newHttpClient();
        for(String route:List.of("/api/v1/me/profile","/api/v1/me/achievements","/api/v1/me/xp-transactions"))
            assertThat(send(guest,"GET",route,null,null,null).statusCode()).isEqualTo(401);
        for(String query:List.of("?page=-1","?size=0","?size=101","?page=2147483647&size=100","?page=x"))
            assertThat(request(player,"GET","/api/v1/me/xp-transactions"+query,null).statusCode()).isEqualTo(400);
        assertThat(request(player,"GET","/api/v1/me/no-such-profile-route",null).statusCode()).isEqualTo(403);
    }
}
