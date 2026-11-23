package com.sidequest.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.config.import=")
@ActiveProfiles("local")
class ModerationOperationsIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Run scripts/test-postgres.sh with isolated PostgreSQL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test
    void onlyProvisionedModeratorCanApproveAndHideReportedQuestsWithAudit() throws Exception {
        Session creator = register();
        Session moderator = register();
        Session reporter = register();
        Session outsider = register();
        var created = call(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "A safe drawing challenge",
                "description", "Draw an animal you can see from a public area.",
                "category", "CREATIVITY", "difficulty", "EASY", "rewardXp", 25)), null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID questId = UUID.fromString(body(created).get("id").asText());
        String etag = created.headers().firstValue("etag").orElseThrow();

        var submitted = call(creator, "POST", "/api/v1/quests/" + questId + "/submit-review", null, etag);
        assertThat(submitted.statusCode()).isEqualTo(202);
        String queue = "/api/v1/moderation/queue";
        assertThat(call(outsider, "GET", queue, null, null).statusCode()).isEqualTo(403);
        assertThat(call(outsider, "POST", "/api/v1/moderation/quests/" + questId + "/decisions",
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "", "expectedVersion", 1)), null)
                .statusCode()).isEqualTo(403);
        assertThat(callAnonymous("GET", queue).statusCode()).isEqualTo(401);

        // Only test harness provisions staff directly. No public endpoint can grant this permission.
        jdbc.update("INSERT INTO moderation_staff (user_id) VALUES (?)", moderator.id());
        var pendingQueue = body(call(moderator, "GET", queue, null, null));
        var pendingItem = pendingQueue.get("content");
        JsonNode item = null;
        for (JsonNode row : pendingItem) if (questId.toString().equals(row.get("questId").asText())) item = row;
        assertThat(item).isNotNull();
        long version = item.get("version").asLong();
        var approved = call(moderator, "POST", "/api/v1/moderation/quests/" + questId + "/decisions",
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "", "expectedVersion", version)), null);
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(body(approved).get("state").asText()).isEqualTo("APPROVED");
        assertThat(call(moderator, "POST", "/api/v1/moderation/quests/" + questId + "/decisions",
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "", "expectedVersion", version)), null)
                .statusCode()).isEqualTo(409);
        assertThat(call(creator, "POST", "/api/v1/quests/" + questId + "/publish", null, etag)
                .statusCode()).isEqualTo(200);

        var received = call(reporter, "POST", "/api/v1/quests/" + questId + "/reports",
                json.writeValueAsString(Map.of("reason", "DANGEROUS_ACT")), null);
        assertThat(received.statusCode()).isEqualTo(201);
        var duplicated = call(reporter, "POST", "/api/v1/quests/" + questId + "/reports",
                json.writeValueAsString(Map.of("reason", "OTHER")), null);
        assertThat(duplicated.statusCode()).isEqualTo(200);
        assertThat(body(received).get("id")).isEqualTo(body(duplicated).get("id"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quest_reports WHERE quest_id = ?", Long.class, questId))
                .isEqualTo(1);
        assertThat(call(creator, "POST", "/api/v1/quests/" + questId + "/reports",
                json.writeValueAsString(Map.of("reason", "OTHER")), null).statusCode()).isEqualTo(400);

        var record = body(approved);
        var hidden = call(moderator, "POST", "/api/v1/moderation/quests/" + questId + "/decisions",
                json.writeValueAsString(Map.of("action", "HIDE", "reasonCode", "DANGEROUS_ACT",
                        "expectedVersion", record.get("version").asLong())), null);
        assertThat(hidden.statusCode()).isEqualTo(200);
        assertThat(body(hidden).get("state").asText()).isEqualTo("HIDDEN");
        assertThat(callAnonymous("GET", "/api/v1/quests/" + questId).statusCode()).isEqualTo(404);
        assertThat(call(reporter, "POST", "/api/v1/quests/" + questId + "/reports",
                json.writeValueAsString(Map.of("reason", "OTHER")), null).statusCode()).isEqualTo(404);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ?", Long.class, questId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ? AND moderator_id = ?",
                Long.class, questId, moderator.id())).isEqualTo(2);
    }

    @Test
    void unprovisionedUserCannotReadPrivateReviewQueueOrFileReportForDraft() throws Exception {
        Session creator = register();
        var draft = call(creator, "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", "Private draft drawing task")), null);
        UUID id = UUID.fromString(body(draft).get("id").asText());
        Session other = register();
        assertThat(call(other, "POST", "/api/v1/quests/" + id + "/reports",
                json.writeValueAsString(Map.of("reason", "PRIVACY")), null).statusCode()).isEqualTo(404);
        assertThat(call(other, "GET", "/api/v1/moderation/queue", null, null).statusCode()).isEqualTo(403);
    }

    @Test
    void creatorCanAppealRejectionButOnlyStaffMayResolveIt() throws Exception {
        Session creator = register();
        Session moderator = register();
        Session outsider = register();
        jdbc.update("INSERT INTO moderation_staff (user_id) VALUES (?)", moderator.id());
        var created = call(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "Learn to draw birds",
                "description", "Sketch birds from a quiet public viewpoint.",
                "category", "CREATIVITY", "difficulty", "EASY", "rewardXp", 25)), null);
        UUID id = UUID.fromString(body(created).get("id").asText());
        String etag = created.headers().firstValue("etag").orElseThrow();
        assertThat(call(creator, "POST", "/api/v1/quests/" + id + "/submit-review", null, etag)
                .statusCode()).isEqualTo(202);
        String decisionUrl = "/api/v1/moderation/quests/" + id + "/decisions";
        var ownerStatus = body(call(creator, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null));
        long reviewVersion = ownerStatus.get("version").asLong();
        var rejected = call(moderator, "POST", decisionUrl,
                json.writeValueAsString(Map.of("action", "REJECT", "reasonCode", "OTHER",
                        "expectedVersion", reviewVersion)), null);
        assertThat(rejected.statusCode()).isEqualTo(200);
        long rejectionVersion = body(rejected).get("version").asLong();
        String appealUrl = "/api/v1/me/quests/" + id + "/appeals";
        var request = json.writeValueAsString(Map.of(
                "reason", "This is a harmless art exercise, please reconsider.",
                "expectedVersion", rejectionVersion));
        assertThat(call(outsider, "POST", appealUrl, request, null).statusCode()).isEqualTo(404);
        assertThat(call(creator, "POST", appealUrl, request, null).statusCode()).isEqualTo(200);
        assertThat(call(creator, "POST", appealUrl, request, null).statusCode()).isEqualTo(409);
        assertThat(body(call(creator, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null))
                .get("state").asText()).isEqualTo("UNDER_REVIEW");
        assertThat(call(creator, "PATCH", "/api/v1/quests/" + id,
                json.writeValueAsString(Map.of("title", "Edited mid-appeal")), etag).statusCode()).isEqualTo(409);

        var queue = body(call(moderator, "GET", "/api/v1/moderation/queue", null, null)).get("content");
        JsonNode pendingAppeal = null;
        for (JsonNode item : queue) if (id.toString().equals(item.get("questId").asText())) pendingAppeal = item;
        assertThat(pendingAppeal).isNotNull();
        assertThat(pendingAppeal.get("appealReason").asText()).contains("harmless art");
        var approved = call(moderator, "POST", decisionUrl,
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "",
                        "expectedVersion", pendingAppeal.get("version").asLong())), null);
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(body(approved).get("state").asText()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM moderation_appeals WHERE quest_id = ?", String.class, id))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ?", Long.class, id))
                .isEqualTo(2);
    }

    @Test
    void moderatorCanDismissFalseReportsWithoutHidingAQuest() throws Exception {
        Session creator = register();
        Session moderator = register();
        Session reporter = register();
        jdbc.update("INSERT INTO moderation_staff (user_id) VALUES (?)", moderator.id());
        var created = call(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "Practice drawing flowers",
                "description", "Draw a simple flower using pencil and paper.",
                "category", "CREATIVITY", "difficulty", "EASY", "rewardXp", 25)), null);
        UUID id = UUID.fromString(body(created).get("id").asText());
        String etag = created.headers().firstValue("etag").orElseThrow();
        assertThat(call(creator, "POST", "/api/v1/quests/" + id + "/submit-review", null, etag)
                .statusCode()).isEqualTo(202);
        var review = body(call(creator, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null));
        var approved = call(moderator, "POST", "/api/v1/moderation/quests/" + id + "/decisions",
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "",
                        "expectedVersion", review.get("version").asLong())), null);
        assertThat(approved.statusCode()).isEqualTo(200);
        assertThat(call(creator, "POST", "/api/v1/quests/" + id + "/publish", null, etag)
                .statusCode()).isEqualTo(200);
        assertThat(call(reporter, "POST", "/api/v1/quests/" + id + "/reports",
                json.writeValueAsString(Map.of("reason", "OTHER")), null).statusCode()).isEqualTo(201);
        var dismissed = call(moderator, "POST", "/api/v1/moderation/quests/" + id + "/decisions",
                json.writeValueAsString(Map.of("action", "DISMISS_REPORTS",
                        "reasonCode", "NO_POLICY_VIOLATION",
                        "expectedVersion", body(approved).get("version").asLong())), null);
        assertThat(dismissed.statusCode()).isEqualTo(200);
        assertThat(body(dismissed).get("state").asText()).isEqualTo("APPROVED");
        assertThat(body(dismissed).get("reports").asInt()).isZero();
        assertThat(callAnonymous("GET", "/api/v1/quests/" + id).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "SELECT resolution FROM quest_reports WHERE quest_id = ?", String.class, id))
                .isEqualTo("DISMISSED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ? AND reason_code = 'NO_POLICY_VIOLATION'",
                Long.class, id)).isEqualTo(1);
    }

    @Test
    void legacyPublishedQuestIsInvisibleUntilAuditedStaffIntakeAndApproval() throws Exception {
        Session creator = register();
        Session staff = register();
        Session outsider = register();
        Session participant = register();
        var created = call(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "Legacy park walking",
                "description", "Take a calm walk in a nearby public park.",
                "category", "FITNESS", "difficulty", "EASY", "rewardXp", 40)), null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(body(created).get("id").asText());
        // Emulate an old PUBLISHED row created before migrations 008-011.
        // Test DB only: production migration must never delete/approve existing records.
        jdbc.update("DELETE FROM quest_moderation WHERE quest_id = ?", id);
        jdbc.update("UPDATE quests SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        String intake = "/api/v1/moderation/legacy/" + id + "/intake";
        assertThat(callAnonymous("GET", "/api/v1/quests/" + id).statusCode()).isEqualTo(404);
        assertThat(call(participant, "POST", "/api/v1/quests/" + id + "/join", null, null)
                .statusCode()).isEqualTo(404);
        assertThat(call(outsider, "GET", "/api/v1/moderation/legacy", null, null).statusCode()).isEqualTo(403);
        assertThat(call(outsider, "POST", intake, null, null).statusCode()).isEqualTo(403);

        jdbc.update("INSERT INTO moderation_staff (user_id) VALUES (?)", staff.id());
        var inventory = body(call(staff, "GET", "/api/v1/moderation/legacy", null, null));
        boolean listed = false;
        for (JsonNode row : inventory.get("content")) {
            if (id.toString().equals(row.get("questId").asText())) listed = true;
        }
        assertThat(listed).isTrue();
        var submitted = call(staff, "POST", intake, null, null);
        assertThat(submitted.statusCode()).isEqualTo(200);
        assertThat(body(submitted).get("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(call(staff, "POST", intake, null, null).statusCode()).isEqualTo(409);
        assertThat(callAnonymous("GET", "/api/v1/quests/" + id).statusCode()).isEqualTo(404);

        var decision = call(staff, "POST", "/api/v1/moderation/quests/" + id + "/decisions",
                json.writeValueAsString(Map.of("action", "APPROVE", "reasonCode", "",
                        "expectedVersion", body(submitted).get("version").asLong())), null);
        assertThat(decision.statusCode()).isEqualTo(200);
        assertThat(body(decision).get("state").asText()).isEqualTo("APPROVED");
        assertThat(callAnonymous("GET", "/api/v1/quests/" + id).statusCode()).isEqualTo(200);
        assertThat(call(participant, "POST", "/api/v1/quests/" + id + "/join", null, null)
                .statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM legacy_moderation_intake WHERE quest_id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ?", Long.class, id)).isEqualTo(2L);
    }

    @Test
    void rejectingLegacyPublishedQuestKeepsItHiddenWithoutAffectingExistingData() throws Exception {
        Session creator = register();
        Session staff = register();
        var created = call(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "Legacy unsafe Quest",
                "description", "A legacy Quest requiring manual review before public display.",
                "category", "ADVENTURE", "difficulty", "HARD", "rewardXp", 25)), null);
        UUID id = UUID.fromString(body(created).get("id").asText());
        jdbc.update("DELETE FROM quest_moderation WHERE quest_id = ?", id);
        jdbc.update("UPDATE quests SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        jdbc.update("INSERT INTO moderation_staff (user_id) VALUES (?)", staff.id());
        var pending = call(staff, "POST", "/api/v1/moderation/legacy/" + id + "/intake", null, null);
        assertThat(pending.statusCode()).isEqualTo(200);
        var hidden = call(staff, "POST", "/api/v1/moderation/quests/" + id + "/decisions",
                json.writeValueAsString(Map.of("action", "REJECT", "reasonCode", "DANGEROUS_ACT",
                        "expectedVersion", body(pending).get("version").asLong())), null);
        assertThat(hidden.statusCode()).isEqualTo(200);
        assertThat(body(hidden).get("state").asText()).isEqualTo("HIDDEN");
        assertThat(callAnonymous("GET", "/api/v1/quests/" + id).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM quests WHERE id = ?", String.class, id)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM moderation_audit WHERE quest_id = ?", Long.class, id)).isEqualTo(2L);
    }

    private Session register() throws Exception {
        HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String username = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        String token = body(send(client, "GET", "/api/v1/auth/csrf", null, null, null))
                .get("csrfToken").asText();
        var response = send(client, "POST", "/api/v1/auth/register",
                json.writeValueAsString(Map.of("email", username + "@example.test", "username", username,
                        "password", "Example test password secure")), token, null);
        assertThat(response.statusCode()).isEqualTo(201);
        return new Session(client, UUID.fromString(body(response).get("id").asText()), tokenFor(client));
    }
    private String tokenFor(HttpClient client) throws Exception {
        return body(send(client, "GET", "/api/v1/auth/csrf", null, null, null))
                .get("csrfToken").asText();
    }
    private JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    private HttpResponse<String> call(Session session, String method, String path,
                                      String jsonBody, String etag) throws Exception {
        return send(session.client(), method, path, jsonBody,
                method.equals("GET") ? null : session.csrf(), etag);
    }
    private HttpResponse<String> callAnonymous(String method, String path) throws Exception {
        HttpClient anon = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        return send(anon, method, path, null, null, null);
    }
    private HttpResponse<String> send(HttpClient client, String method, String path,
                                      String jsonBody, String token, String etag) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) request.header("X-CSRF-TOKEN", token);
        if (etag != null) request.header("If-Match", etag);
        if (jsonBody != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private record Session(HttpClient client, UUID id, String csrf) { }
}
