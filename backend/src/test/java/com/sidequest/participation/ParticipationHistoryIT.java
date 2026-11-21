package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.reward.*;
import java.net.http.HttpClient;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ParticipationHistoryIT extends ParticipationApiSupport {
    @Autowired XpLedgerRepository ledger;

    @Test
    void filtersPagesAndReturnsArchivedSummariesWithOriginalAwards() throws Exception {
        var creator = register(); var player = register(); var other = register();
        var firstQuest = quest(creator, true); var secondQuest = quest(creator, true);
        UUID first = UUID.fromString(body(join(player, firstQuest.id())).get("id").asText());
        UUID second = UUID.fromString(body(join(player, secondQuest.id())).get("id").asText());
        join(other, firstQuest.id());
        tx().executeWithoutResult(status -> {
            var p = participations.findById(first).orElseThrow();
            p.complete(p.getJoinedAt()); participations.flush(); ledger.save(XpLedger.award(p, p.getCompletedAt()));
        });
        assertThat(archive(creator, firstQuest).statusCode()).isEqualTo(200);
        String path = "/api/v1/me/participations";
        var result = request(player, "GET", path, null);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.headers().firstValue("cache-control")).contains("no-store");
        assertThat(body(result).get("size").asInt()).isEqualTo(20);
        assertThat(body(result).get("totalElements").asInt()).isEqualTo(2);
        var page = body(request(player, "GET", path + "?size=1", null));
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(second.toString());
        assertThat(page.get("hasNext").asBoolean()).isTrue();
        assertThat(body(request(player, "GET", path + "?size=1&page=1", null)).get("content").get(0).get("id").asText()).isEqualTo(first.toString());
        assertThat(body(request(player, "GET", path + "?page=99", null)).get("content")).isEmpty();
        for (String query : List.of("?status=COMPLETED", "?questId=" + firstQuest.id(), "?status=COMPLETED&questId=" + firstQuest.id())) {
            var filtered = body(request(player, "GET", path + query, null));
            assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
            assertThat(filtered.get("content").get(0).get("id").asText()).isEqualTo(first.toString());
        }
        assertThat(body(request(player, "GET", path + "?status=IN_PROGRESS&questId=" + firstQuest.id(), null)).get("content")).isEmpty();
        var detail = body(request(player, "GET", path + "/" + first, null));
        assertThat(detail.get("quest").get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(detail.get("rewardXp").asInt()).isEqualTo(175);
        assertThat(detail.get("awardedXp").asInt()).isEqualTo(175);
        assertThat(detail.has("userId")).isFalse();
        assertThat(detail.get("quest").has("creatorId")).isFalse();
        assertThat(body(request(player, "GET", path + "/" + second, null)).get("awardedXp").asInt()).isZero();
        assertThat(request(player, "GET", "/api/v1/quests/" + firstQuest.id(), null).statusCode()).isEqualTo(404);
    }

    @Test
    void concealsOwnershipAndValidatesAllParameters() throws Exception {
        var owner = register(); var player = register(); var other = register();
        var quest = quest(owner, true); var id = body(join(player, quest.id())).get("id").asText();
        String path = "/api/v1/me/participations";
        assertThat(send(HttpClient.newHttpClient(), "GET", path, null, null, null).statusCode()).isEqualTo(401);
        assertThat(send(HttpClient.newHttpClient(), "GET", path + "/" + id, null, null, null).statusCode()).isEqualTo(401);
        assertThat(request(other, "GET", path + "/" + id, null).statusCode()).isEqualTo(404);
        assertThat(request(player, "GET", path + "/" + UUID.randomUUID(), null).statusCode()).isEqualTo(404);
        assertThat(body(request(other, "GET", path + "?questId=" + quest.id(), null)).get("content")).isEmpty();
        for (String query : List.of("?status=BAD", "?status=", "?questId=bad", "?questId=1-1-1-1-1",
                "?page=-1", "?page=2147483648", "?page=2147483647&size=100", "?size=0", "?size=101", "?page=x")) {
            var response = request(player, "GET", path + query, null);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("application/problem+json");
        }
        assertThat(request(player, "GET", path + "/bad", null).statusCode()).isEqualTo(400);
        assertThat(request(player, "GET", "/api/v1/me/unimplemented", null).statusCode()).isEqualTo(403);
    }

    @Test
    void projectionHasBoundedQueryCountAndStableTieOrdering() throws Exception {
        var owner = register(); var player = register(); var quest = quest(owner, true);
        var ids = new ArrayList<UUID>();
        // Direct SQL supplies equal timestamps without mutating immutable snapshots.
        for (int i = 0; i < 5; i++) {
            UUID qid = UUID.randomUUID(); UUID pid = UUID.randomUUID(); ids.add(pid);
            jdbc.update("INSERT INTO quests SELECT ?, creator_id, title, description, category, difficulty, reward_xp, status, created_at, updated_at, published_at, archived_at, version FROM quests WHERE id=?", qid, quest.id());
            jdbc.update("INSERT INTO participations(id,user_id,quest_id,status,reward_xp,joined_at) VALUES(?,?,?,'IN_PROGRESS',175,'2026-01-01T00:00:00Z')", pid, player.id(), qid);
        }
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var page = service.history(player.id(), null, null, 0, 3);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2); // scalar page plus count, regardless of page size
        assertThat(page.content()).extracting(ParticipationDtos.View::id)
                .containsExactlyElementsOf(ids.stream().sorted(Comparator.comparing(UUID::toString).reversed()).limit(3).toList());
        stats.clear();
        service.detail(player.id(), ids.get(0));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }
}
