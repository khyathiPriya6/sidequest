package com.sidequest.group;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.participation.ParticipationApiSupport;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class GroupJourneyIT extends ParticipationApiSupport {
    private HttpResponse<String> call(Session actor,String method,String path,String jsonBody) throws Exception {
        return send(actor.client(),method,path,jsonBody,csrf(actor.client()),null);
    }
    private String username(Session actor) throws Exception {
        return body(call(actor,"GET","/api/v1/auth/me",null)).get("username").asText();
    }
    private String jsonBody(String key,Object value) throws Exception {
        return json.writeValueAsString(Map.of(key,value));
    }
    private UUID becomeFriends(Session owner,Session friend) throws Exception {
        var send=call(owner,"POST","/api/v1/me/friend-requests",jsonBody("username",username(friend)));
        assertThat(send.statusCode()).as(send.body()).isEqualTo(200);
        UUID request=UUID.fromString(body(send).get("id").asText());
        var accepted=call(friend,"POST","/api/v1/me/friend-requests/"+request+"/accept",
                jsonBody("expectedVersion",0));
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        return request;
    }
    private UUID createTeam(Session owner) throws Exception {
        var result=call(owner,"POST","/api/v1/teams",jsonBody("name","The Trail Collective"));
        assertThat(result.statusCode()).as(result.body()).isEqualTo(201);
        return UUID.fromString(body(result).get("id").asText());
    }
    private UUID inviteAndAccept(Session owner,Session friend,UUID team) throws Exception {
        var sent=call(owner,"POST","/api/v1/teams/"+team+"/invitations",
                jsonBody("username",username(friend)));
        assertThat(sent.statusCode()).as(sent.body()).isEqualTo(200);
        UUID invitation=UUID.fromString(body(sent).get("id").asText());
        var accepted=call(friend,"POST","/api/v1/me/team-invitations/"+invitation+"/accept",
                jsonBody("expectedVersion",0));
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        return invitation;
    }
    @Test
    void invitationRequiresFriendshipAndAcceptedMembershipIsPrivate() throws Exception {
        var owner=register();var friend=register();var stranger=register();
        UUID team=createTeam(owner);
        String url="/api/v1/teams/"+team;
        assertThat(call(stranger,"GET",url,null).statusCode()).isEqualTo(404);
        assertThat(call(stranger,"GET",url+"/members",null).statusCode()).isEqualTo(404);
        assertThat(call(stranger,"GET",url+"/leaderboard",null).statusCode()).isEqualTo(404);
        assertThat(call(owner,"POST",url+"/invitations",
                jsonBody("username",username(friend))).statusCode()).isEqualTo(404);
        becomeFriends(owner,friend);
        var invitation=call(owner,"POST",url+"/invitations",
                jsonBody("username",username(friend)));
        assertThat(invitation.statusCode()).as(invitation.body()).isEqualTo(200);
        var item=body(invitation);
        UUID id=UUID.fromString(item.get("id").asText());
        assertThat(call(owner,"POST",url+"/invitations",
                jsonBody("username",username(friend))).statusCode()).isEqualTo(200);
        assertThat(call(stranger,"POST","/api/v1/me/team-invitations/"+id+"/accept",
                jsonBody("expectedVersion",0)).statusCode()).isEqualTo(404);
        assertThat(body(call(friend,"GET","/api/v1/me/team-invitations",null))
                .get("content").size()).isEqualTo(1);
        var accepted=call(friend,"POST","/api/v1/me/team-invitations/"+id+"/accept",
                jsonBody("expectedVersion",0));
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(call(friend,"GET",url,null).statusCode()).isEqualTo(200);
        assertThat(body(call(friend,"GET",url+"/members",null)).get("totalElements").asInt()).isEqualTo(2);
        assertThat(call(friend,"POST",url+"/invitations",
                jsonBody("username",username(stranger))).statusCode()).isEqualTo(404);
        assertThat(call(friend,"POST",url+"/archive","{}").statusCode()).isEqualTo(404);
        assertThat(call(owner,"POST",url+"/leave","{}").statusCode()).isEqualTo(409);
        assertThat(call(friend,"POST",url+"/leave","{}").statusCode()).isEqualTo(200);
        assertThat(call(friend,"GET",url,null).statusCode()).isEqualTo(404);
        assertThat(call(owner,"POST",url+"/archive","{}").statusCode()).isEqualTo(200);
        assertThat(body(call(owner,"GET",url,null)).get("state").asText()).isEqualTo("ARCHIVED");
    }
    @Test
    void challengeCountsEligiblePersonalXpExactlyOnceWithoutExtraAward() throws Exception {
        var owner=register();var friend=register();var creator=register();var stranger=register();
        becomeFriends(owner,friend);
        UUID team=createTeam(owner);
        inviteAndAccept(owner,friend,team);
        var published=quest(creator,true);
        var draft=quest(creator,false);
        String url="/api/v1/teams/"+team;
        var invalid=call(owner,"POST",url+"/challenges",json.writeValueAsString(Map.of(
                "questId",draft.id(),"days",7,"targetCompletions",2)));
        assertThat(invalid.statusCode()).isEqualTo(404);
        var challenge=call(owner,"POST",url+"/challenges",json.writeValueAsString(Map.of(
                "questId",published.id(),"days",7,"targetCompletions",2)));
        assertThat(challenge.statusCode()).as(challenge.body()).isEqualTo(201);
        assertThat(body(challenge).get("completed").asInt()).isZero();
        assertThat(body(call(owner,"GET",url+"/challenges",null))
                .get("content").get(0).get("earnedXp").asInt()).isZero();
        var enrolled=join(friend,published.id());
        assertThat(enrolled.statusCode()).isEqualTo(201);
        var participation=UUID.fromString(body(enrolled).get("id").asText());
        var completed=call(friend,"POST","/api/v1/participations/"+participation+"/complete",null);
        assertThat(completed.statusCode()).as(completed.body()).isEqualTo(200);
        assertThat(body(completed).get("awardedXp").asInt()).isEqualTo(175);
        var progress=body(call(friend,"GET",url+"/challenges",null)).get("content").get(0);
        assertThat(progress.get("completed").asLong()).isEqualTo(1);
        assertThat(progress.get("earnedXp").asInt()).isEqualTo(175);
        var board=body(call(owner,"GET",url+"/leaderboard",null));
        assertThat(board.get("totalElements").asInt()).isEqualTo(2);
        assertThat(board.get("content").get(0).get("earnedXp").asInt()).isEqualTo(175);
        assertThat(call(stranger,"GET",url+"/challenges",null).statusCode()).isEqualTo(404);
        assertThat(call(friend,"POST","/api/v1/participations/"+participation+"/complete",null).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_ledger WHERE participation_id=?",
                Long.class,participation)).isEqualTo(1);
        assertThat(body(call(owner,"GET",url+"/challenges",null)).get("content").get(0)
                .get("completed").asInt()).isEqualTo(1);
        // Archiving must preserve already eligible personal XP and group progress.
        assertThat(archive(creator,published).statusCode()).isEqualTo(200);
        assertThat(body(call(owner,"GET",url+"/challenges",null)).get("content").get(0)
                .get("earnedXp").asInt()).isEqualTo(175);
        // A later safety hide removes only derived team eligibility, never personal XP history.
        jdbc.update("UPDATE quest_moderation SET state='HIDDEN' WHERE quest_id=?",published.id());
        var hidden=body(call(owner,"GET",url+"/challenges",null)).get("content").get(0);
        assertThat(hidden.get("earnedXp").asInt()).isZero();
        assertThat(hidden.get("title").asText()).isEqualTo("Quest unavailable");
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM xp_ledger WHERE participation_id=?",
                Long.class,participation)).isEqualTo(175);
        assertThat(call(owner,"POST",url+"/challenges",json.writeValueAsString(Map.of(
                "questId",published.id(),"days",7,"targetCompletions",2))).statusCode()).isEqualTo(404);
    }

    @Test
    void concurrentAcceptOfSameInvitationCannotDuplicateMembership() throws Exception {
        var owner=register();var player=register();
        becomeFriends(owner,player);
        UUID team=createTeam(owner);
        var sent=call(owner,"POST","/api/v1/teams/"+team+"/invitations",
                jsonBody("username",username(player)));
        UUID invitation=UUID.fromString(body(sent).get("id").asText());
        var ready=new java.util.concurrent.CountDownLatch(2);
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> accept=()->{
                ready.countDown();
                assertThat(start.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return call(player,"POST","/api/v1/me/team-invitations/"+invitation+"/accept",
                        jsonBody("expectedVersion",0)).statusCode();
            };
            var first=pool.submit(accept);
            var second=pool.submit(accept);
            assertThat(ready.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,404);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM team_members WHERE team_id=? AND user_id=? AND state='ACTIVE'
                """,Long.class,team,player.id())).isEqualTo(1);
        } finally {
            start.countDown();pool.shutdownNow();
        }
    }


    @Test
    void concurrentInvitationsCannotExceedTeamCapacity() throws Exception {
        var owner=register();
        UUID team=createTeam(owner);
        // Build 19 active members inside the disposable database, then race the last seat.
        for(int i=0;i<18;i++){
            var filler=register();
            jdbc.update("""
                INSERT INTO team_members(team_id,user_id,role,state)
                VALUES(?,?,'MEMBER','ACTIVE')
                """,team,filler.id());
        }
        var one=register();var two=register();
        becomeFriends(owner,one);becomeFriends(owner,two);
        var a=call(owner,"POST","/api/v1/teams/"+team+"/invitations",
                jsonBody("username",username(one)));
        var b=call(owner,"POST","/api/v1/teams/"+team+"/invitations",
                jsonBody("username",username(two)));
        UUID aid=UUID.fromString(body(a).get("id").asText());
        UUID bid=UUID.fromString(body(b).get("id").asText());
        var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{
                assertThat(start.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return call(one,"POST","/api/v1/me/team-invitations/"+aid+"/accept",
                        jsonBody("expectedVersion",0)).statusCode();
            });
            var second=pool.submit(()->{
                assertThat(start.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                return call(two,"POST","/api/v1/me/team-invitations/"+bid+"/accept",
                        jsonBody("expectedVersion",0)).statusCode();
            });
            start.countDown();
            assertThat(java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15,java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM team_members WHERE team_id=? AND state='ACTIVE'
                """,Long.class,team)).isEqualTo(20);
        } finally {start.countDown();pool.shutdownNow();}
    }

    @Test
    void guestCsrfAndPageValidationAreRejected() throws Exception {
        var owner=register();UUID team=createTeam(owner);
        var guest=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        assertThat(send(guest,"GET","/api/v1/teams/"+team,null,null,null).statusCode()).isEqualTo(401);
        assertThat(send(owner.client(),"POST","/api/v1/teams/"+team+"/archive","{}",null,null)
                .statusCode()).isEqualTo(403);
        assertThat(call(owner,"GET","/api/v1/teams/"+team+"/members?size=0",null).statusCode())
                .isEqualTo(400);
        assertThat(call(owner,"GET","/api/v1/teams/"+team+"/leaderboard?page=-1",null).statusCode())
                .isEqualTo(400);
        assertThat(call(owner,"POST","/api/v1/teams/"+team+"/challenges",
                json.writeValueAsString(Map.of("questId",UUID.randomUUID(),"days",0,
                    "targetCompletions",2))).statusCode()).isEqualTo(400);
    }
}
