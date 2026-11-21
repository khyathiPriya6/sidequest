package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class ParticipationCompletionIT extends ParticipationApiSupport {
    private String path(UUID id) { return "/api/v1/participations/" + id + "/complete"; }
    private UUID enroll(Session player, QuestData quest) throws Exception {
        return UUID.fromString(body(join(player, quest.id())).get("id").asText());
    }
    private long awards(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM xp_ledger WHERE participation_id=?", Long.class, id);
    }

    @Test
    void archivedCompletionIsPermanentAndRetriesPreserveSavedAward() throws Exception {
        var owner = register(); var player = register(); var quest = quest(owner, true);
        UUID id = enroll(player, quest);
        assertThat(archive(owner, quest).statusCode()).isEqualTo(200);
        var first = request(player, "POST", path(id), null);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("cache-control")).contains("no-store");
        var saved = body(first);
        assertThat(saved.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(saved.get("rewardXp").asInt()).isEqualTo(175);
        assertThat(saved.get("awardedXp").asInt()).isEqualTo(175);
        assertThat(saved.get("quest").get("status").asText()).isEqualTo("ARCHIVED");
        var award = jdbc.queryForMap("SELECT id, amount, awarded_at FROM xp_ledger WHERE participation_id=?", id);
        assertThat(request(player, "POST", path(id), null).statusCode()).isEqualTo(200);
        assertThat(body(request(player, "POST", path(id), null))).isEqualTo(saved);
        assertThat(body(join(player, quest.id()))).isEqualTo(saved);
        assertThat(body(request(player, "GET", "/api/v1/me/participations/" + id, null))).isEqualTo(saved);
        assertThat(jdbc.queryForMap("SELECT id, amount, awarded_at FROM xp_ledger WHERE participation_id=?", id)).isEqualTo(award);
        assertThat(awards(id)).isEqualTo(1);
        assertThat(Instant.parse(saved.get("completedAt").asText())).isAfterOrEqualTo(Instant.parse(saved.get("joinedAt").asText()));
        assertThat(request(player, "GET", "/api/v1/quests/" + quest.id(), null).statusCode()).isEqualTo(404);
    }

    @Test
    void protectsOwnerCsrfBodyAndIdentifiers() throws Exception {
        var owner = register(); var player = register(); var other = register();
        UUID id = enroll(player, quest(owner, true));
        var guest = HttpClient.newBuilder().cookieHandler(new java.net.CookieManager()).build();
        assertThat(send(guest, "POST", path(id), null, csrf(guest), null).statusCode()).isEqualTo(401);
        assertThat(send(player.client(), "POST", path(id), null, null, null).statusCode()).isEqualTo(403);
        assertThat(request(other, "POST", path(id), null).statusCode()).isEqualTo(404);
        assertThat(request(player, "POST", path(UUID.randomUUID()), null).statusCode()).isEqualTo(404);
        assertThat(request(player, "POST", "/api/v1/participations/1-1-1-1-1/complete", null).statusCode()).isEqualTo(400);
        assertThat(request(player, "POST", path(id), "{}").statusCode()).isEqualTo(400);
        assertThat(awards(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM participations WHERE id=?", String.class, id)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void concurrentHttpCompletionsWaitAndAwardExactlyOnce() throws Exception {
        var owner = register(); var player = register(); UUID id = enroll(player, quest(owner, true));
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            var holder = executor.submit(() -> tx().execute(status -> {
                participations.findByIdAndUserForUpdate(id, player.id()).orElseThrow();
                locked.countDown(); await(release); return null;
            }));
            await(locked);
            var first = executor.submit(() -> request(player, "POST", path(id), null));
            var second = executor.submit(() -> request(player, "POST", path(id), null));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean bothWaiting = false;
            while (System.nanoTime() < deadline) {
                if (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Long.class) >= 2) { bothWaiting = true; break; }
                Thread.sleep(20);
            }
            assertThat(bothWaiting).isTrue();
            release.countDown(); holder.get(10, TimeUnit.SECONDS);
            var a = first.get(10, TimeUnit.SECONDS); var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.statusCode()).isEqualTo(200); assertThat(b.statusCode()).isEqualTo(200);
            assertThat(body(a)).isEqualTo(body(b)); assertThat(awards(id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(amount) FROM xp_ledger WHERE participation_id=?", Long.class, id)).isEqualTo(175);
        } finally { release.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void rollsBackStatusWhenTheDatabaseRejectsTheAward() throws Exception {
        var owner = register(); var player = register(); UUID id = enroll(player, quest(owner, true));
        // Isolated test cluster only: deliberately reject this particular award after the status UPDATE.
        jdbc.execute("CREATE FUNCTION test_reject_completion_award() RETURNS trigger AS $$ BEGIN IF NEW.participation_id = '" + id + "'::uuid THEN RAISE EXCEPTION 'test private SQL failure' USING ERRCODE='23514'; END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER test_reject_completion_award BEFORE INSERT ON xp_ledger FOR EACH ROW EXECUTE FUNCTION test_reject_completion_award()");
        try {
            var failed = request(player, "POST", path(id), null);
            assertThat(failed.statusCode()).isEqualTo(500);
            assertThat(body(failed).get("code").asText()).isEqualTo("integrity_error");
            assertThat(failed.body()).doesNotContain("SQL", "test private", "xp_ledger");
            assertThat(jdbc.queryForObject("SELECT status FROM participations WHERE id=?", String.class, id)).isEqualTo("IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT completed_at FROM participations WHERE id=?", Object.class, id)).isNull();
            assertThat(awards(id)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER test_reject_completion_award ON xp_ledger");
            jdbc.execute("DROP FUNCTION test_reject_completion_award()");
        }
        assertThat(request(player, "POST", path(id), null).statusCode()).isEqualTo(200);
        assertThat(awards(id)).isEqualTo(1);
    }

    @Test
    void rejectsMissingMismatchedAndPrematureLedgerWithoutMintingRewards() throws Exception {
        var owner = register(); var player = register();
        for (String corruption : new String[] {"missing", "amount", "time", "in_progress"}) {
            UUID id = enroll(player, quest(owner, true));
            if (!corruption.equals("in_progress")) {
                jdbc.update("UPDATE participations SET status='COMPLETED', completed_at=joined_at WHERE id=?", id);
            }
            if (!corruption.equals("missing")) {
                jdbc.update("INSERT INTO xp_ledger(id,participation_id,amount,awarded_at) SELECT ?,id,?,joined_at + (? * interval '1 second') FROM participations WHERE id=?",
                        UUID.randomUUID(), corruption.equals("amount") ? 176 : 175, corruption.equals("time") ? -1 : 0, id);
            }
            long before = awards(id);
            var response = request(player, "POST", path(id), null);
            assertThat(response.statusCode()).as(corruption).isEqualTo(500);
            assertThat(body(response).get("code").asText()).isEqualTo("integrity_error");
            assertThat(awards(id)).isEqualTo(before);
        }
    }
}
