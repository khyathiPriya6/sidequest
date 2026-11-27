package com.sidequest.team;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.participation.ParticipationApiSupport;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Exercises real authenticated HTTP against disposable PostgreSQL. */
class TeamIT extends ParticipationApiSupport {
    private String serialize(Object value) throws Exception {return json.writeValueAsString(value);}
    private java.net.http.HttpResponse<String> call(Session actor,String method,String path,String data) throws Exception {
        return send(actor.client(),method,path,data,csrf(actor.client()),null);
    }
    private String username(Session who) throws Exception {
        return body(call(who,"GET","/api/v1/auth/me",null)).get("username").asText();
    }
    private UUID create(Session owner) throws Exception {
        var response=call(owner,"POST","/api/v1/me/teams",serialize(Map.of("name","The Comet Crew")));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return UUID.fromString(body(response).get("id").asText());
    }
    @Test
    void inviteConsentPrivateRosterAndLeaderboardReuseExistingXp() throws Exception {
        var owner=register();var friend=register();var stranger=register();
        UUID team=create(owner);
        assertThat(call(stranger,"GET","/api/v1/teams/"+team,null).statusCode()).isEqualTo(404);
        assertThat(call(stranger,"GET","/api/v1/teams/"+team+"/members",null).statusCode()).isEqualTo(404);
        assertThat(body(call(owner,"GET","/api/v1/teams/"+team+"/members",null)).get("content").size()).isEqualTo(1);

        var invite=call(owner,"POST","/api/v1/teams/"+team+"/invites",
            serialize(Map.of("username",username(friend))));
        assertThat(invite.statusCode()).as(invite.body()).isEqualTo(200);
        UUID inviteId=UUID.fromString(body(invite).get("id").asText());
        assertThat(call(stranger,"POST","/api/v1/me/team-invites/"+inviteId+"/accept",
            "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        assertThat(call(friend,"GET","/api/v1/teams/"+team,null).statusCode()).isEqualTo(404);
        assertThat(call(owner,"POST","/api/v1/me/team-invites/"+inviteId+"/accept",
            "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);

        var accepted=call(friend,"POST","/api/v1/me/team-invites/"+inviteId+"/accept",
            "{\"expectedVersion\":0}");
        assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(200);
        assertThat(body(accepted).get("state").asText()).isEqualTo("ACCEPTED");
        assertThat(call(friend,"POST","/api/v1/me/team-invites/"+inviteId+"/accept",
            "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
        assertThat(body(call(friend,"GET","/api/v1/teams/"+team+"/members",null))
            .get("content").size()).isEqualTo(2);

        // A self-reported published Quest is the *only* source of group XP.
        var published=quest(owner,true);
        java.time.Instant start=java.time.Instant.now().minusSeconds(300);
        java.time.Instant end=start.plusSeconds(3600);
        var challenge=call(owner,"POST","/api/v1/teams/"+team+"/challenges",
            serialize(Map.of("questId",published.id(),"startsAt",start.toString(),"endsAt",end.toString())));
        assertThat(challenge.statusCode()).as(challenge.body()).isEqualTo(201);
        assertThat(body(challenge).get("earnedXp").asLong()).isZero();
        assertThat(call(stranger,"GET","/api/v1/teams/"+team+"/leaderboard",null).statusCode()).isEqualTo(404);
        assertThat(join(friend,published.id()).statusCode()).isEqualTo(201);
        var participation=body(call(friend,"GET","/api/v1/me/participations?questId="+published.id(),null))
            .get("content").get(0).get("id").asText();
        var completion=call(friend,"POST","/api/v1/participations/"+participation+"/complete",null);
        assertThat(completion.statusCode()).as(completion.body()).isEqualTo(200);
        var progress=body(call(friend,"GET","/api/v1/teams/"+team+"/challenges",null))
            .get("content").get(0);
        assertThat(progress.get("completedMembers").asInt()).isEqualTo(1);
        assertThat(progress.get("earnedXp").asLong()).isEqualTo(175);
        var board=body(call(friend,"GET","/api/v1/teams/"+team+"/leaderboard",null));
        assertThat(board.get("content").size()).isEqualTo(2);
        assertThat(board.get("content").get(0).get("earnedXp").asLong()).isEqualTo(175);
        assertThat(call(friend,"POST","/api/v1/participations/"+participation+"/complete",null).statusCode())
            .isEqualTo(200);
        assertThat(body(call(friend,"GET","/api/v1/teams/"+team+"/challenges",null))
            .get("content").get(0).get("earnedXp").asLong()).isEqualTo(175);

        assertThat(call(owner,"DELETE","/api/v1/teams/"+team+"/members/"+username(friend),null).statusCode())
            .isEqualTo(204);
        assertThat(call(friend,"GET","/api/v1/teams/"+team+"/leaderboard",null).statusCode()).isEqualTo(404);
        assertThat(body(call(owner,"GET","/api/v1/teams/"+team+"/leaderboard",null)).get("content").size())
            .isEqualTo(1);
    }

    @Test
    void blocksBadInvitesAndDisallowsPrivateQuestChallenges() throws Exception {
        var owner=register();var peer=register();var outsider=register();
        UUID team=create(owner);
        var draft=quest(owner,false);
        var now=java.time.Instant.now();
        var input=serialize(Map.of("questId",draft.id(),"startsAt",now.toString(),
            "endsAt",now.plusSeconds(600).toString()));
        assertThat(call(owner,"POST","/api/v1/teams/"+team+"/challenges",input).statusCode()).isEqualTo(404);
        assertThat(call(outsider,"POST","/api/v1/teams/"+team+"/invites",
            serialize(Map.of("username",username(peer)))).statusCode()).isEqualTo(404);
        assertThat(call(owner,"POST","/api/v1/teams/"+team+"/invites",
            serialize(Map.of("username",username(owner)))).statusCode()).isEqualTo(400);
        assertThat(call(owner,"GET","/api/v1/teams/"+team+"/members?size=101",null).statusCode()).isEqualTo(400);
        assertThat(call(owner,"DELETE","/api/v1/teams/"+team+"/members/"+username(owner),null).statusCode())
            .isEqualTo(409);
        var invite=call(owner,"POST","/api/v1/teams/"+team+"/invites",
            serialize(Map.of("username",username(peer))));
        UUID id=UUID.fromString(body(invite).get("id").asText());
        assertThat(call(owner,"POST","/api/v1/teams/"+team+"/invites/"+id+"/cancel",
            "{\"expectedVersion\":0}").statusCode()).isEqualTo(204);
        assertThat(call(peer,"POST","/api/v1/me/team-invites/"+id+"/accept",
            "{\"expectedVersion\":0}").statusCode()).isEqualTo(404);
    }

    @Test
    void simultaneousAcceptsCanOnlyAddOneMembership() throws Exception {
        var owner=register();var peer=register();
        UUID team=create(owner);
        var invite=call(owner,"POST","/api/v1/teams/"+team+"/invites",
            serialize(Map.of("username",username(peer))));
        UUID id=UUID.fromString(body(invite).get("id").asText());
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            Callable<Integer> accept=()->{
                gate.await(5,TimeUnit.SECONDS);
                return call(peer,"POST","/api/v1/me/team-invites/"+id+"/accept",
                    "{\"expectedVersion\":0}").statusCode();
            };
            var one=executor.submit(accept);var two=executor.submit(accept);gate.countDown();
            assertThat(java.util.List.of(one.get(10,TimeUnit.SECONDS),two.get(10,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,404);
            assertThat(body(call(owner,"GET","/api/v1/teams/"+team+"/members",null))
                .get("totalElements").asInt()).isEqualTo(2);
        } finally {executor.shutdownNow();}
    }
}
