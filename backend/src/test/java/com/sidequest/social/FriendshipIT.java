package com.sidequest.social;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.participation.ParticipationApiSupport;
import java.net.http.HttpClient;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Isolated PostgreSQL HTTP integration; run via backend/scripts/test-postgres.sh. */
class FriendshipIT extends ParticipationApiSupport {
    private String jsonBody(String username) throws Exception {
        return json.writeValueAsString(Map.of("username",username));
    }
    private String name(Session user) throws Exception {
        return body(request(user,"GET","/api/v1/auth/me",null)).get("username").asText();
    }
    private String settings(Session user,String visibility,long version) throws Exception {
        return body(request(user,"PATCH","/api/v1/me/social/settings",
                json.writeValueAsString(Map.of("visibility",visibility,"expectedVersion",version))))
                .get("visibility").asText();
    }
    private String opaque(Session owner) throws Exception {
        return body(request(owner,"GET","/api/v1/me/social/settings",null)).get("profileId").asText();
    }
    @Test void requestsRequireConsentBlockRevokesAndPrivacyFollowsFriendship() throws Exception {
        var alice=register();var bob=register();var stranger=register();
        String aliceId=opaque(alice),bobId=opaque(bob);
        assertThat(settings(alice,"FRIENDS",0)).isEqualTo("FRIENDS");
        String path="/api/v1/social/profiles/"+aliceId;
        assertThat(request(bob,"GET",path,null).statusCode()).isEqualTo(404);
        var invited=request(bob,"POST","/api/v1/me/friend-requests",jsonBody(name(alice)));
        assertThat(invited.statusCode()).isEqualTo(200);
        var invite=body(invited);
        assertThat(invite.get("state").asText()).isEqualTo("PENDING");
        assertThat(invite.get("incoming").asBoolean()).isFalse();
        assertThat(request(bob,"POST","/api/v1/me/friend-requests",jsonBody(name(alice))).statusCode()).isEqualTo(200);
        assertThat(request(alice,"POST","/api/v1/me/friend-requests",jsonBody(name(bob))).statusCode()).isEqualTo(409);
        assertThat(request(stranger,"GET","/api/v1/me/friend-requests?direction=incoming",null).statusCode()).isEqualTo(200);
        assertThat(body(request(stranger,"GET","/api/v1/me/friend-requests?direction=incoming",null))
                .get("content").size()).isZero();
        assertThat(request(stranger,"POST","/api/v1/me/friend-requests/"+invite.get("id").asText()+"/accept",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        assertThat(request(bob,"POST","/api/v1/me/friend-requests/"+invite.get("id").asText()+"/accept",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        var accepted=request(alice,"POST","/api/v1/me/friend-requests/"+invite.get("id").asText()+"/accept",
                "{\"expectedVersion\":0}");
        assertThat(accepted.statusCode()).isEqualTo(200);
        assertThat(body(accepted).get("state").asText()).isEqualTo("ACCEPTED");
        assertThat(request(alice,"POST","/api/v1/me/friend-requests/"+invite.get("id").asText()+"/accept",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        assertThat(request(bob,"GET",path,null).statusCode()).isEqualTo(200);
        assertThat(request(stranger,"GET",path,null).statusCode()).isEqualTo(404);
        var board=request(bob,"GET","/api/v1/me/friends/leaderboard?period=all",null);
        assertThat(board.statusCode()).isEqualTo(200);
        assertThat(board.headers().firstValue("cache-control")).contains("no-store");
        assertThat(board.body()).doesNotContain("email","password","userId");
        assertThat(request(bob,"GET","/api/v1/me/friends",null).statusCode()).isEqualTo(200);
        assertThat(body(request(bob,"GET","/api/v1/me/friends",null)).get("content").size()).isEqualTo(1);
        var blocked=request(alice,"POST","/api/v1/me/social/blocks",jsonBody(name(bob)));
        assertThat(blocked.statusCode()).isEqualTo(204);
        assertThat(request(bob,"GET",path,null).statusCode()).isEqualTo(404);
        assertThat(request(bob,"POST","/api/v1/me/friend-requests",jsonBody(name(alice))).statusCode()).isEqualTo(404);
        assertThat(body(request(bob,"GET","/api/v1/me/friends",null)).get("content").size()).isZero();
        assertThat(request(alice,"GET","/api/v1/social/profiles/"+bobId,null).statusCode()).isEqualTo(404);
    }

    @Test void invalidAndUnauthorizedSocialRoutesAreDenied() throws Exception {
        var player=register();
        var guest=java.net.http.HttpClient.newBuilder()
            .cookieHandler(new java.net.CookieManager(null,java.net.CookiePolicy.ACCEPT_ALL)).build();
        assertThat(request(player,"POST","/api/v1/me/friend-requests",jsonBody(name(player))).statusCode()).isEqualTo(400);
        assertThat(request(player,"POST","/api/v1/me/friend-requests",jsonBody("nonexistent_abc_xyz")).statusCode()).isEqualTo(404);
        assertThat(request(player,"GET","/api/v1/me/friend-requests?direction=invalid",null).statusCode()).isEqualTo(400);
        assertThat(request(player,"GET","/api/v1/me/friends/leaderboard?period=invalid",null).statusCode()).isEqualTo(400);
        assertThat(send(player.client(),"POST","/api/v1/me/social/blocks",jsonBody("another_user"),
                null,null).statusCode()).isEqualTo(403);
        assertThat(send(guest,"GET","/api/v1/me/friends",null,null,null).statusCode()).isEqualTo(401);
    }


    @Test void friendsBoardUsesEligibleXpAndExcludesStrangers() throws Exception {
        var creator=register();var alice=register();var bob=register();var outsider=register();
        var requestFriend=request(alice,"POST","/api/v1/me/friend-requests",jsonBody(name(bob)));
        assertThat(requestFriend.statusCode()).isEqualTo(200);
        var id=body(requestFriend).get("id").asText();
        assertThat(request(bob,"POST","/api/v1/me/friend-requests/"+id+"/accept",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(200);
        java.time.Instant at=java.time.Instant.now().minusSeconds(300);
        for(var player:java.util.List.of(alice,bob,outsider)) {
            var quest=UUID.randomUUID(); var participation=UUID.randomUUID();
            jdbc.update("""
                INSERT INTO quests(id,creator_id,title,description,category,difficulty,reward_xp,status,created_at,updated_at,published_at,version)
                VALUES(?,?,'Friend score','An approved, safe challenge.','LEARNING','EASY',90,'PUBLISHED',?,?,?,0)
                """,quest,creator.id(),java.sql.Timestamp.from(at.minusSeconds(60)),
                java.sql.Timestamp.from(at.minusSeconds(60)),java.sql.Timestamp.from(at.minusSeconds(60)));
            jdbc.update("INSERT INTO quest_moderation(quest_id,state,policy_version,version) VALUES(?,'APPROVED','v1',0)",quest);
            jdbc.update("""
                INSERT INTO participations(id,user_id,quest_id,status,reward_xp,joined_at,completed_at)
                VALUES(?,?,?,'COMPLETED',90,?,?)
                """,participation,player.id(),quest,java.sql.Timestamp.from(at.minusSeconds(40)),
                java.sql.Timestamp.from(at));
            jdbc.update("INSERT INTO xp_ledger(id,participation_id,amount,awarded_at) VALUES(?,?,90,?)",
                    UUID.randomUUID(),participation,java.sql.Timestamp.from(at));
        }
        var board=request(alice,"GET","/api/v1/me/friends/leaderboard?period=all",null);
        assertThat(board.statusCode()).isEqualTo(200);
        assertThat(body(board).get("totalElements").asLong()).isEqualTo(2);
        assertThat(body(board).get("content").size()).isEqualTo(2);
        assertThat(board.body()).doesNotContain(name(outsider));
        for(var row:body(board).get("content")) {
            assertThat(row.get("rank").asLong()).isEqualTo(1);
            assertThat(row.get("earnedXp").asLong()).isEqualTo(90);
        }
        assertThat(request(alice,"GET","/api/v1/me/friends/leaderboard?period=all&size=1&page=1",null).statusCode()).isEqualTo(200);
        assertThat(body(request(alice,"GET","/api/v1/me/friends/leaderboard?period=all&size=1&page=1",null))
                .get("content").size()).isEqualTo(1);
        for(String path:java.util.List.of("/api/v1/me/friends?size=0",
                "/api/v1/me/friend-requests?size=101",
                "/api/v1/me/friends/leaderboard?size=0")) {
            assertThat(request(alice,"GET",path,null).statusCode()).isEqualTo(400);
        }
    }

    @Test void cancellationAndRemovalUseVersionedTransitions() throws Exception {
        var alice=register();var bob=register();
        var send=request(alice,"POST","/api/v1/me/friend-requests",jsonBody(name(bob)));
        assertThat(send.statusCode()).isEqualTo(200);
        String id=body(send).get("id").asText();
        assertThat(request(bob,"POST","/api/v1/me/friend-requests/"+id+"/cancel",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        var cancelled=request(alice,"POST","/api/v1/me/friend-requests/"+id+"/cancel",
                "{\"expectedVersion\":0}");
        assertThat(cancelled.statusCode()).isEqualTo(200);
        assertThat(body(cancelled).get("state").asText()).isEqualTo("CANCELLED");
        assertThat(request(alice,"POST","/api/v1/me/friend-requests/"+id+"/cancel",
                "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        assertThat(request(alice,"POST","/api/v1/me/friend-requests",jsonBody(name(bob))).statusCode()).isEqualTo(429);
    }
}
