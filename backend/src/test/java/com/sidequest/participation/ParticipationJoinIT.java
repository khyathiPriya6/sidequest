package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.quest.QuestFixtures;
import java.net.http.HttpClient;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ParticipationJoinIT extends ParticipationApiSupport {
    @Test
    void joinsOnceAndRetainsArchivedAccessWithoutAwardingXp() throws Exception {
        var creator = register(); var player = register(); var quest = quest(creator, true);
        var first = join(player, quest.id());
        assertThat(first.statusCode()).isEqualTo(201);
        var saved = body(first);
        assertThat(first.headers().firstValue("location")).contains("/api/v1/me/participations/" + saved.get("id").asText());
        assertThat(first.headers().firstValue("cache-control")).contains("no-store");
        assertThat(saved.get("rewardXp").asInt()).isEqualTo(175);
        assertThat(saved.get("awardedXp").asInt()).isZero();
        assertThat(saved.get("quest").get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(body(join(player, quest.id()))).isEqualTo(saved);
        assertThat(archive(creator, quest).statusCode()).isEqualTo(200);
        var repeat = join(player, quest.id());
        assertThat(repeat.statusCode()).isEqualTo(200);
        assertThat(body(repeat).get("id")).isEqualTo(saved.get("id"));
        assertThat(body(repeat).get("joinedAt")).isEqualTo(saved.get("joinedAt"));
        assertThat(body(repeat).get("quest").get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(request(player, "GET", "/api/v1/quests/" + quest.id(), null).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_ledger WHERE participation_id=?", Long.class,
                UUID.fromString(saved.get("id").asText()))).isZero();
    }

    @Test
    void enforcesSecurityEligibilityAndBodylessParameters() throws Exception {
        var owner = register(); var player = register(); var published = quest(owner, true); var draft = quest(owner, false);
        String path = "/api/v1/quests/" + published.id() + "/join";
        var guest = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        assertThat(send(guest, "POST", path, null, csrf(guest), null).statusCode()).isEqualTo(401);
        assertThat(send(player.client(), "POST", path, null, null, null).statusCode()).isEqualTo(403);
        assertThat(body(join(owner, published.id())).get("code").asText()).isEqualTo("self_enrollment_forbidden");
        assertThat(join(owner, draft.id()).statusCode()).isEqualTo(409);
        assertThat(join(player, draft.id()).statusCode()).isEqualTo(404);
        assertThat(join(player, UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(request(player, "POST", path, "{}").statusCode()).isEqualTo(400);
        assertThat(request(player, "POST", "/api/v1/quests/1-1-1-1-1/join", null).statusCode()).isEqualTo(400);
        assertThat(archive(owner, published).statusCode()).isEqualTo(200);
        assertThat(join(player, published.id()).statusCode()).isEqualTo(404);
    }

    @Test
    void concurrentHttpJoinsReturnOneEnrollment() throws Exception {
        var owner = register(); var player = register(); var quest = quest(owner, true);
        var start = new CountDownLatch(1); var ready = new CountDownLatch(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<java.net.http.HttpResponse<String>> call = () -> { ready.countDown(); await(start); return join(player, quest.id()); };
            var first = executor.submit(call); var second = executor.submit(call);
            await(ready); start.countDown();
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a.statusCode(), b.statusCode())).containsExactlyInAnyOrder(201, 200);
            assertThat(body(a)).isEqualTo(body(b));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM participations WHERE user_id=? AND quest_id=?", Long.class, player.id(), quest.id())).isEqualTo(1);
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void serializesBothJoinArchiveOrderings() throws Exception {
        var owner = register(); var player = register();
        for (boolean joinFirst : new boolean[] {true, false}) {
            var quest = quest(owner, true);
            var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var holder = executor.submit(() -> tx().execute(status -> {
                    if (joinFirst) service.join(player.id(), quest.id());
                    else {
                        var q = quests.findByIdAndCreatorForUpdate(quest.id(), owner.id()).orElseThrow();
                        QuestFixtures.archived(q); quests.flush();
                    }
                    locked.countDown(); await(release); return null;
                }));
                await(locked);
                var contender = executor.submit(() -> joinFirst ? archive(owner, quest) : join(player, quest.id()));
                awaitDatabaseLockWait(); release.countDown(); holder.get(10, TimeUnit.SECONDS);
                assertThat(contender.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(joinFirst ? 200 : 404);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM participations WHERE user_id=? AND quest_id=?", Long.class, player.id(), quest.id())).isEqualTo(joinFirst ? 1 : 0);
            } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
        }
    }
}
