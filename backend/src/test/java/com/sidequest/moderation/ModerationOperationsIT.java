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
