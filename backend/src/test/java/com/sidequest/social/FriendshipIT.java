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
