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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.config.import=")
@ActiveProfiles("local")
class QuestApiIT {
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
    void createsReadsAndPatchesOnlyOwnedDraftsWithConditionalWrites() throws Exception {
        Session owner = register();
        Session other = register();

        HttpResponse<String> created = write(owner, "POST", "/api/v1/quests",
                Map.of("title", "Explore a new walking route"), null);
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode draft = json.readTree(created.body());
        UUID id = UUID.fromString(draft.get("id").asText());
        assertThat(draft.get("creatorId").asText()).isEqualTo(owner.userId());
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.has("version")).isFalse();
        String initialEtag = created.headers().firstValue("etag").orElseThrow();
        assertThat(initialEtag).matches("\"\\d+\"");
        assertThat(created.headers().firstValue("location").orElseThrow())
                .isEqualTo("/api/v1/quests/" + id);

        HttpResponse<String> list = send(owner, "GET", "/api/v1/me/quests", null, null, null);
        assertThat(list.statusCode()).isEqualTo(200);
        JsonNode page = json.readTree(list.body());
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(id.toString());
        JsonNode draftPage = json.readTree(send(owner, "GET", "/api/v1/me/quests?status=DRAFT", null, null, null).body());
        assertThat(draftPage.get("totalElements").asLong()).isEqualTo(1);
        JsonNode publishedPage = json.readTree(send(owner, "GET", "/api/v1/me/quests?status=PUBLISHED", null, null, null).body());
        assertThat(publishedPage.get("content").size()).isZero();

        HttpResponse<String> detail = send(owner, "GET", "/api/v1/me/quests/" + id, null, null, null);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.headers().firstValue("etag").orElseThrow()).isEqualTo(initialEtag);

        assertThat(send(other, "GET", "/api/v1/me/quests/" + id, null, null, null).statusCode())
                .isEqualTo(404);
        assertThat(write(other, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Another person's private quest"), initialEtag).statusCode()).isEqualTo(404);
        assertThat(write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Missing precondition"), null).statusCode()).isEqualTo(428);
        assertThat(write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Malformed precondition"), "W/" + initialEtag).statusCode()).isEqualTo(400);

        HttpResponse<String> stale = write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Stale edit"), "\"999\"");
        assertThat(stale.statusCode()).isEqualTo(412);
        assertThat(stale.headers().firstValue("etag").orElseThrow()).isEqualTo(initialEtag);
        assertThat(json.readTree(send(owner, "GET", "/api/v1/me/quests/" + id, null, null, null).body())
                .get("title").asText()).isEqualTo("Explore a new walking route");

        HttpResponse<String> updated = write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Revised walking route",
                        "description", "A description long enough to save.",
                        "category", "LEARNING",
                        "difficulty", "MEDIUM",
                        "rewardXp", 250),
                initialEtag);
        assertThat(updated.statusCode()).isEqualTo(200);
        JsonNode revised = json.readTree(updated.body());
        assertThat(revised.get("title").asText()).isEqualTo("Revised walking route");
        assertThat(revised.get("description").asText()).isEqualTo("A description long enough to save.");
        assertThat(revised.get("category").asText()).isEqualTo("LEARNING");
        String updatedEtag = updated.headers().firstValue("etag").orElseThrow();
        assertThat(updatedEtag).isNotEqualTo(initialEtag);

        HttpResponse<String> invalidPatch = write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("category", "UNKNOWN"), updatedEtag);
        assertThat(invalidPatch.statusCode()).isEqualTo(422);
        assertThat(fieldNames(json.readTree(invalidPatch.body()))).contains("category");

        HttpResponse<String> cannotClearTitle = send(owner.client(), "PATCH", "/api/v1/quests/" + id,
                "{\"title\":null}", csrf(owner.client()), updatedEtag);
        assertThat(cannotClearTitle.statusCode()).isEqualTo(422);
        assertThat(fieldNames(json.readTree(cannotClearTitle.body()))).contains("title");

        HttpResponse<String> clearOptional = send(owner.client(), "PATCH", "/api/v1/quests/" + id,
                "{\"description\":null}", csrf(owner.client()), updatedEtag);
        assertThat(clearOptional.statusCode()).isEqualTo(200);
        assertThat(json.readTree(clearOptional.body()).get("description").isNull()).isTrue();
        updatedEtag = clearOptional.headers().firstValue("etag").orElseThrow();

        HttpResponse<String> noOp = write(owner, "PATCH", "/api/v1/quests/" + id,
                Map.of("title", "Revised walking route"), updatedEtag);
        assertThat(noOp.statusCode()).isEqualTo(200);
        assertThat(noOp.headers().firstValue("etag").orElseThrow()).isEqualTo(updatedEtag);

        assertThat(send(owner, "GET", "/api/v1/me/quests?page=-1", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(owner, "GET", "/api/v1/me/quests?size=0", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(owner, "GET", "/api/v1/me/quests?size=101", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(owner, "GET", "/api/v1/me/quests?status=BOGUS", null, null, null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void validatesDraftFieldsAndRejectsClientManagedProperties() throws Exception {
        Session owner = register();
        HttpResponse<String> invalid = write(owner, "POST", "/api/v1/quests",
                Map.of("title", "🧭🧭",
                        "description", "short",
                        "category", "UNKNOWN",
                        "difficulty", "HARD",
                        "rewardXp", 1001),
                null);
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(invalid.headers().firstValue("content-type").orElseThrow())
                .startsWith("application/problem+json");
        JsonNode problem = json.readTree(invalid.body());
        assertThat(problem.get("code").asText()).isEqualTo("validation_failed");
        assertThat(fieldNames(problem)).contains("title", "description", "category", "rewardXp");

        HttpResponse<String> spoofed = write(owner, "POST", "/api/v1/quests",
                Map.of("title", "A valid quest title", "creatorId", UUID.randomUUID().toString()),
                null);
        assertThat(spoofed.statusCode()).isEqualTo(400);
        assertThat(json.readTree(spoofed.body()).get("code").asText()).isEqualTo("malformed_request");

        HttpResponse<String> missingTitle = write(owner, "POST", "/api/v1/quests", Map.of(), null);
        assertThat(missingTitle.statusCode()).isEqualTo(422);
        assertThat(fieldNames(json.readTree(missingTitle.body()))).contains("title");

        HttpResponse<String> blankTitle = write(owner, "POST", "/api/v1/quests",
                Map.of("title", "   "), null);
        JsonNode blankTitleProblem = json.readTree(blankTitle.body());
        assertThat(blankTitle.statusCode()).isEqualTo(422);
        assertThat(blankTitleProblem.get("errors").get(0).get("code").asText()).isEqualTo("required");
    }

    @Test
    void ownerEndpointsRequireAuthenticationAndWritesRequireCsrf() throws Exception {
        HttpClient anonymous = client();
        assertThat(send(anonymous, "GET", "/api/v1/me/quests", null, null, null).statusCode()).isEqualTo(401);

        String token = csrf(anonymous);
        HttpResponse<String> unauthenticated = send(anonymous, "POST", "/api/v1/quests",
                "{\"title\":\"A valid quest title\"}", token, null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);

        Session owner = register();
        HttpResponse<String> csrfRejected = send(owner.client(), "POST", "/api/v1/quests",
                "{\"title\":\"A valid quest title\"}", null, null);
        assertThat(csrfRejected.statusCode()).isEqualTo(403);
    }

    private Session register() throws Exception {
        HttpClient client = client();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String email = username + "@example.test";
        String body = json.writeValueAsString(Map.of(
                "email", email,
                "username", username,
                "password", "exact password 🔒 value"));
        HttpResponse<String> result = send(client, "POST", "/api/v1/auth/register", body, csrf(client), null);
        assertThat(result.statusCode()).isEqualTo(201);
        return new Session(client, json.readTree(result.body()).get("id").asText());
    }

    private HttpResponse<String> write(Session session, String method, String path, Object body, String etag)
            throws Exception {
        String serialized = body == null ? null : json.writeValueAsString(body);
        return send(session.client(), method, path, serialized, csrf(session.client()), etag);
    }

    private String csrf(HttpClient client) throws Exception {
        HttpResponse<String> response = send(client, "GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("csrfToken").asText();
    }

    private HttpResponse<String> send(
            Session session, String method, String path, String body, String csrf, String etag) throws Exception {
        return send(session.client(), method, path, body, csrf, etag);
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

    private static HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
    }

    private static java.util.List<String> fieldNames(JsonNode problem) {
        var fields = new java.util.ArrayList<String>();
        problem.get("errors").forEach(error -> fields.add(error.get("field").asText()));
        return fields;
    }

    private record Session(HttpClient client, String userId) { }
}
