package com.sidequest.participation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.CookieManager;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * M7-01: real HTTP journey against an isolated PostgreSQL cluster.
 *
 * Unlike individual domain tests, this verifies that browser/mobile clients can
 * navigate the complete API lifecycle with session cookies, CSRF, staff
 * permission checks and exactly-once XP. All writes are to the disposable
 * database provided by scripts/test-postgres.sh.
 */
class PlayerJourneyIT extends ParticipationApiSupport {
    private String jsonBody(Map<String, ?> value) throws Exception {
        return json.writeValueAsString(value);
    }

    @Test
    void creatorModeratorAndPlayerCompleteOneApprovedQuestWithoutDuplicateXp() throws Exception {
        var creator = register();
        var staff = register();
        var player = register();
        var guest = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        // Accounts have separate authenticated sessions; the player cannot read staff controls.
        assertThat(request(creator, "GET", "/api/v1/auth/me", null).statusCode()).isEqualTo(200);
        assertThat(request(player, "GET", "/api/v1/auth/me", null).statusCode()).isEqualTo(200);
        assertThat(send(guest, "GET", "/api/v1/me/profile", null, null, null).statusCode()).isEqualTo(401);
        assertThat(request(player, "GET", "/api/v1/moderation/queue", null).statusCode()).isEqualTo(403);

        var created = request(creator, "POST", "/api/v1/quests", jsonBody(Map.of(
                "title", "M7 safe creative journey",
                "description", "Sketch a landscape with pencil and paper.",
                "category", "CREATIVITY", "difficulty", "EASY", "rewardXp", 80)));
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        UUID questId = UUID.fromString(body(created).get("id").asText());
        String etag = created.headers().firstValue("etag").orElseThrow();
        String url = "/api/v1/quests/" + questId;

        // A draft is not discoverable or joinable by another account.
        assertThat(send(guest, "GET", url, null, null, null).statusCode()).isEqualTo(404);
        assertThat(request(player, "POST", url + "/join", null).statusCode()).isEqualTo(404);
        assertThat(send(creator.client(), "POST", url + "/submit-review", null, null, etag)
                .statusCode()).isEqualTo(403);
        assertThat(send(creator.client(), "POST", url + "/submit-review", null, creator.csrf(), etag)
                .statusCode()).isEqualTo(202);
        assertThat(send(creator.client(), "POST", url + "/publish", null, creator.csrf(), etag)
                .statusCode()).isEqualTo(409);

        // Only the test fixture may provision moderator permissions.
        jdbc.update("INSERT INTO moderation_staff(user_id) VALUES (?)", staff.id());
        var queueResponse = request(staff, "GET", "/api/v1/moderation/queue", null);
        assertThat(queueResponse.statusCode()).isEqualTo(200);
        JsonNode pending = null;
        for (JsonNode row : body(queueResponse).get("content")) {
            if (questId.toString().equals(row.get("questId").asText())) {
                pending = row;
                break;
            }
        }
        assertThat(pending).isNotNull();
        long version = pending.get("version").asLong();
        String decisionUrl = "/api/v1/moderation/quests/" + questId + "/decisions";
        String approval = jsonBody(Map.of(
                "action", "APPROVE", "reasonCode", "", "expectedVersion", version));
        assertThat(request(player, "POST", decisionUrl, approval).statusCode()).isEqualTo(403);
        var approved = request(staff, "POST", decisionUrl, approval);
        assertThat(approved.statusCode()).as(approved.body()).isEqualTo(200);
        assertThat(body(approved).get("state").asText()).isEqualTo("APPROVED");

        var published = send(creator.client(), "POST", url + "/publish", null, creator.csrf(), etag);
        assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
        assertThat(body(published).get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(send(guest, "GET", url, null, null, null).statusCode()).isEqualTo(200);

        // Self-enrolment is forbidden even for a published, approved quest.
        var selfJoin = request(creator, "POST", url + "/join", null);
        assertThat(selfJoin.statusCode()).isEqualTo(403);
        assertThat(body(selfJoin).get("code").asText()).isEqualTo("self_enrollment_forbidden");
        var joined = request(player, "POST", url + "/join", null);
        assertThat(joined.statusCode()).as(joined.body()).isEqualTo(201);
        UUID participationId = UUID.fromString(body(joined).get("id").asText());
        assertThat(body(joined).get("status").asText()).isEqualTo("IN_PROGRESS");
        String completeUrl = "/api/v1/participations/" + participationId + "/complete";

        // Missing CSRF and another user's session cannot complete the participant's quest.
        assertThat(send(player.client(), "POST", completeUrl, null, null, null)
                .statusCode()).isEqualTo(403);
        assertThat(request(creator, "POST", completeUrl, null).statusCode()).isEqualTo(404);
        var completed = request(player, "POST", completeUrl, null);
        assertThat(completed.statusCode()).as(completed.body()).isEqualTo(200);
        assertThat(body(completed).get("awardedXp").asInt()).isEqualTo(80);
        assertThat(body(completed).get("status").asText()).isEqualTo("COMPLETED");

        // A repeated completion is idempotent at both HTTP and ledger boundaries.
        assertThat(body(request(player, "POST", completeUrl, null))).isEqualTo(body(completed));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM xp_ledger WHERE participation_id=?",
                Long.class, participationId)).isEqualTo(1L);
        var profile = request(player, "GET", "/api/v1/me/profile", null);
        assertThat(profile.statusCode()).isEqualTo(200);
        assertThat(body(profile).get("totalXp").asLong()).isEqualTo(80L);
        assertThat(body(profile).get("completedQuests").asLong()).isEqualTo(1L);
        assertThat(body(request(player, "GET", "/api/v1/me/xp-transactions", null))
                .get("totalElements").asLong()).isEqualTo(1L);
        assertThat(body(request(creator, "GET", "/api/v1/me/profile", null))
                .get("totalXp").asLong()).isZero();

        var rankResponse = request(player, "GET", "/api/v1/me/leaderboard-rank?period=all", null);
        assertThat(rankResponse.statusCode()).as(rankResponse.body()).isEqualTo(200);
        assertThat(rankResponse.body()).contains("80");

        // The player may see their own participation, not another account's records.
        assertThat(request(player, "GET", "/api/v1/me/participations/" + participationId, null)
                .statusCode()).isEqualTo(200);
        assertThat(request(creator, "GET", "/api/v1/me/participations/" + participationId, null)
                .statusCode()).isEqualTo(404);
    }
}
