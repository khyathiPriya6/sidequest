package com.sidequest.quest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.config.import=")
@ActiveProfiles("local")
class QuestLifecycleApiIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Use backend/scripts/test-postgres.sh");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test
    void enforcesPublicationValidationOwnershipAndIdempotentLifecycle() throws Exception {
        Session owner = register();
        Session other = register();
        HttpResponse<String> created = create(owner, Map.of("title", "A meaningful quest title"));
        UUID id = UUID.fromString(json.readTree(created.body()).get("id").asText());
        String draftEtag = etag(created);

        HttpClient anonymous = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        String anonymousCsrf = csrf(anonymous);
        assertThat(send(anonymous, "POST", path(id, "publish"), null, anonymousCsrf, draftEtag).statusCode())
                .isEqualTo(401);
        assertThat(send(anonymous, "POST", path(id, "archive"), null, anonymousCsrf, draftEtag).statusCode())
                .isEqualTo(401);
        assertThat(action(other, "publish", id, draftEtag).statusCode()).isEqualTo(404);
        assertThat(action(other, "archive", id, draftEtag).statusCode()).isEqualTo(404);
        assertThat(send(owner, "POST", path(id, "publish"), null, null).statusCode()).isEqualTo(428);

        HttpResponse<String> invalid = action(owner, "publish", id, draftEtag);
        assertThat(invalid.statusCode()).isEqualTo(422);
        JsonNode invalidProblem = json.readTree(invalid.body());
        assertThat(fieldNames(invalidProblem)).containsExactly("description", "category", "difficulty", "rewardXp");
        JsonNode unchangedDraft = json.readTree(getQuest(owner, id).body());
        assertThat(unchangedDraft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(etag(getQuest(owner, id))).isEqualTo(draftEtag);

        HttpResponse<String> completedDraft = send(owner, "PATCH", "/api/v1/quests/" + id,
                json.writeValueAsString(Map.of("description", "A complete description for this quest.",
                        "category", "LEARNING", "difficulty", "MEDIUM", "rewardXp", 200)),
                draftEtag);
        assertThat(completedDraft.statusCode()).isEqualTo(200);
        String completeEtag = etag(completedDraft);

        HttpResponse<String> published = action(owner, "publish", id, completeEtag);
        assertThat(published.statusCode()).isEqualTo(200);
        JsonNode publishedBody = json.readTree(published.body());
        assertThat(publishedBody.get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publishedBody.get("publishedAt").isNull()).isFalse();
        assertThat(publishedBody.get("archivedAt").isNull()).isTrue();
        String publishedEtag = etag(published);

        HttpResponse<String> publishRetry = action(owner, "publish", id, draftEtag);
        assertThat(publishRetry.statusCode()).isEqualTo(200);
        assertThat(etag(publishRetry)).isEqualTo(publishedEtag);
        JsonNode retriedPublish = json.readTree(publishRetry.body());
        assertThat(retriedPublish.get("publishedAt")).isEqualTo(publishedBody.get("publishedAt"));
        assertThat(retriedPublish.get("updatedAt")).isEqualTo(publishedBody.get("updatedAt"));

        assertThat(send(owner, "PATCH", "/api/v1/quests/" + id,
                json.writeValueAsString(Map.of("title", "Published quests are immutable")), publishedEtag)
                .statusCode()).isEqualTo(409);

        HttpResponse<String> archived = action(owner, "archive", id, publishedEtag);
        assertThat(archived.statusCode()).isEqualTo(200);
        JsonNode archivedBody = json.readTree(archived.body());
        assertThat(archivedBody.get("status").asText()).isEqualTo("ARCHIVED");
        assertThat(archivedBody.get("publishedAt")).isEqualTo(publishedBody.get("publishedAt"));
        assertThat(archivedBody.get("archivedAt").isNull()).isFalse();
        String archivedEtag = etag(archived);

        HttpResponse<String> archiveRetry = action(owner, "archive", id, completeEtag);
        assertThat(archiveRetry.statusCode()).isEqualTo(200);
        assertThat(etag(archiveRetry)).isEqualTo(archivedEtag);
        assertThat(json.readTree(archiveRetry.body()).get("archivedAt"))
                .isEqualTo(archivedBody.get("archivedAt"));
        assertThat(action(owner, "publish", id, archivedEtag).statusCode()).isEqualTo(409);

        HttpResponse<String> secondDraft = create(owner, Map.of("title", "A second meaningful quest"));
        UUID secondId = UUID.fromString(json.readTree(secondDraft.body()).get("id").asText());
        HttpResponse<String> draftArchive = action(owner, "archive", secondId, etag(secondDraft));
        assertThat(draftArchive.statusCode()).isEqualTo(200);
        assertThat(json.readTree(draftArchive.body()).get("status").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    void concurrentPublishAndArchiveAllowOnlyOneTransitionToWin() throws Exception {
        Session owner = register();
        HttpResponse<String> created = create(owner, Map.of(
                "title", "A race-safe quest title",
                "description", "A complete description for this quest.",
                "category", "ADVENTURE",
                "difficulty", "EASY",
                "rewardXp", 100));
        UUID id = UUID.fromString(json.readTree(created.body()).get("id").asText());
        String etag = etag(created);
        String csrf = csrf(owner.client());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var publish = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendWithToken(owner, "POST", path(id, "publish"), null, csrf, etag);
            });
            var archive = executor.submit(() -> {
                ready.countDown();
                start.await();
                return sendWithToken(owner, "POST", path(id, "archive"), null, csrf, etag);
            });
            ready.await();
            start.countDown();
            HttpResponse<String> publishResult = publish.get();
            HttpResponse<String> archiveResult = archive.get();
            List<Integer> statuses = new ArrayList<>(List.of(publishResult.statusCode(), archiveResult.statusCode()));
            assertThat(statuses).contains(200);
            assertThat(statuses.stream().filter(status -> status == 200).count()).isEqualTo(1);
            assertThat(statuses).allMatch(status -> status == 200 || status == 409 || status == 412);
            for (HttpResponse<String> response : List.of(publishResult, archiveResult)) {
                if (response.statusCode() == 412) {
                    assertThat(response.headers().firstValue("etag")).isPresent();
                }
            }
        }

        JsonNode finalQuest = json.readTree(getQuest(owner, id).body());
        assertThat(finalQuest.get("status").asText()).isIn("PUBLISHED", "ARCHIVED");
    }

    private HttpResponse<String> create(Session session, Object body) throws Exception {
        return send(session, "POST", "/api/v1/quests", json.writeValueAsString(body), null);
    }

    private HttpResponse<String> action(Session session, String action, UUID id, String etag) throws Exception {
        return send(session, "POST", path(id, action), null, etag);
    }

    private HttpResponse<String> getQuest(Session session, UUID id) throws Exception {
        return send(session, "GET", "/api/v1/me/quests/" + id, null, null);
    }

    private HttpResponse<String> send(
            Session session, String method, String path, String body, String etag) throws Exception {
        String csrf = method.equals("GET") ? null : csrf(session.client());
        return sendWithToken(session, method, path, body, csrf, etag);
    }

    private HttpResponse<String> sendWithToken(
            Session session, String method, String path, String body, String csrf, String etag) throws Exception {
        return send(session.client(), method, path, body, csrf, etag);
    }

    private Session register() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String body = json.writeValueAsString(Map.of(
                "email", username + "@example.test",
                "username", username,
                "password", "exact password 🔒 value"));
        HttpResponse<String> result = send(client, "POST", "/api/v1/auth/register", body, csrf(client), null);
        assertThat(result.statusCode()).isEqualTo(201);
        return new Session(client, json.readTree(result.body()).get("id").asText());
    }

    private HttpResponse<String> send(
            HttpClient client, String method, String path, String body, String csrf, String etag) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        if (etag != null) request.header("If-Match", etag);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method,
                        body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrf(HttpClient client) throws Exception {
        HttpResponse<String> response = send(client, "GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("csrfToken").asText();
    }

    private static String path(UUID id, String action) {
        return "/api/v1/quests/" + id + "/" + action;
    }

    private static String etag(HttpResponse<String> response) {
        return response.headers().firstValue("etag").orElseThrow();
    }

    private static List<String> fieldNames(JsonNode problem) {
        var fields = new ArrayList<String>();
        problem.get("errors").forEach(error -> fields.add(error.get("field").asText()));
        return fields;
    }

    private record Session(HttpClient client, String userId) { }
}
