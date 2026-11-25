package com.sidequest.social;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.participation.ParticipationApiSupport;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/** Real HTTP/cookie/CSRF and PostgreSQL tests using the disposable isolation harness. */
class SocialProfileIT extends ParticipationApiSupport {
    private static final String SETTINGS = "/api/v1/me/social/settings";

    @Test
    void privateByDefaultOptInAndFriendsFailClosed() throws Exception {
        var alice = register();
        var bob = register();
        var initial = request(alice, "GET", SETTINGS, null);
        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.headers().firstValue("cache-control")).contains("no-store");
        var preferences = body(initial);
        assertThat(preferences.get("visibility").asText()).isEqualTo("PRIVATE");
        assertThat(preferences.get("version").asLong()).isZero();
        var opaque = UUID.fromString(preferences.get("profileId").asText());
        assertThat(opaque).isNotEqualTo(alice.id());
        assertThat(body(request(alice, "GET", SETTINGS, null)).get("profileId").asText())
                .isEqualTo(opaque.toString());

        String path = "/api/v1/social/profiles/" + opaque;
        var privateView = request(bob, "GET", path, null);
        var nonexistent = request(bob, "GET", "/api/v1/social/profiles/" + UUID.randomUUID(), null);
        assertThat(privateView.statusCode()).isEqualTo(404);
        assertThat(nonexistent.statusCode()).isEqualTo(404);
        assertThat(body(privateView).get("code").asText()).isEqualTo(body(nonexistent).get("code").asText());
        assertThat(send(HttpClient.newHttpClient(), "GET", path, null, null, null).statusCode()).isEqualTo(401);

        var friends = request(alice, "PATCH", SETTINGS, """
                {"visibility":"FRIENDS","expectedVersion":0}
                """);
        assertThat(friends.statusCode()).isEqualTo(200);
        assertThat(request(bob, "GET", path, null).statusCode()).isEqualTo(404);

        var madePublic = request(alice, "PATCH", SETTINGS, """
                {"visibility":"PUBLIC","expectedVersion":1}
                """);
        assertThat(madePublic.statusCode()).isEqualTo(200);
        assertThat(body(madePublic).get("version").asLong()).isEqualTo(2);
        var publicView = request(bob, "GET", path, null);
        assertThat(publicView.statusCode()).isEqualTo(200);
        assertThat(publicView.headers().firstValue("cache-control")).contains("no-store");
        assertThat(body(publicView).get("profileId").asText()).isEqualTo(opaque.toString());
        assertThat(body(publicView).get("displayName").asText()).isNotBlank();
        assertThat(publicView.body()).doesNotContain("email", "password", "createdAt", "totalXp",
                "userId", "xpTransactions", "achievements", "status");

        var hide = request(alice, "PATCH", SETTINGS, """
                {"visibility":"PRIVATE","expectedVersion":2}
                """);
        assertThat(hide.statusCode()).isEqualTo(200);
        assertThat(request(bob, "GET", path, null).statusCode()).isEqualTo(404);
        assertThat(request(alice, "GET", path, null).statusCode()).isEqualTo(200);
    }

    @Test
    void validatesWritesCsrfStaleVersionAndDisabledAccounts() throws Exception {
        var owner = register();
        var viewer = register();
        var settings = body(request(owner, "GET", SETTINGS, null));
        var path = "/api/v1/social/profiles/" + settings.get("profileId").asText();

        assertThat(send(owner.client(), "PATCH", SETTINGS,
                "{\"visibility\":\"PUBLIC\",\"expectedVersion\":0}", null, null).statusCode()).isEqualTo(403);
        assertThat(send(HttpClient.newHttpClient(), "PATCH", SETTINGS,
                "{\"visibility\":\"PUBLIC\",\"expectedVersion\":0}", null, null).statusCode()).isEqualTo(401);
        assertThat(request(owner, "PATCH", SETTINGS, "{\"visibility\":\"BAD\",\"expectedVersion\":0}").statusCode()).isEqualTo(400);
        assertThat(request(owner, "PATCH", SETTINGS, "{\"visibility\":\"PUBLIC\",\"expectedVersion\":-1}").statusCode()).isEqualTo(400);
        assertThat(request(owner, "PATCH", SETTINGS, "{\"visibility\":\"PUBLIC\"}").statusCode()).isEqualTo(400);

        assertThat(request(owner, "PATCH", SETTINGS,
                "{\"visibility\":\"PUBLIC\",\"expectedVersion\":0}").statusCode()).isEqualTo(200);
        var stale = request(owner, "PATCH", SETTINGS,
                "{\"visibility\":\"PRIVATE\",\"expectedVersion\":0}");
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(body(stale).get("code").asText()).isEqualTo("version_conflict");
        assertThat(request(viewer, "GET", path, null).statusCode()).isEqualTo(200);

        jdbc.update("UPDATE users SET status='DISABLED' WHERE id=?", owner.id());
        assertThat(request(viewer, "GET", path, null).statusCode()).isEqualTo(404);
    }

    @Test
    void concurrentVisibilityUpdatesHaveSingleWinner() throws Exception {
        var owner = register();
        request(owner, "GET", SETTINGS, null);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            Callable<Integer> update = () -> {
                start.await(5, TimeUnit.SECONDS);
                return request(owner, "PATCH", SETTINGS,
                        "{\"visibility\":\"PUBLIC\",\"expectedVersion\":0}").statusCode();
            };
            var a = pool.submit(update);
            var b = pool.submit(update);
            start.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(body(request(owner, "GET", SETTINGS, null)).get("version").asLong()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
