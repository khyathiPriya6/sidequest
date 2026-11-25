package com.sidequest.quest;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sidequest.moderation.QuestModerationRepository;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.config.import=")
@ActiveProfiles("local")
class QuestPublicApiIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired QuestRepository quests;
    @Autowired QuestModerationRepository moderation;

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

    @BeforeEach
    void clearQuestFixtures() {
        moderation.deleteAllInBatch();
        quests.deleteAllInBatch();
    }

    @Test
    void publicCatalogueStartsEmptyAndUsesBoundedPagination() throws Exception {
        HttpClient anonymous = client();
        HttpResponse<String> response = send(anonymous, "GET", "/api/v1/quests", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode page = json.readTree(response.body());
        assertThat(page.get("content").size()).isZero();
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalElements").asLong()).isZero();
        assertThat(page.get("totalPages").asInt()).isZero();
        assertThat(page.get("hasNext").asBoolean()).isFalse();
        assertThat(send(anonymous, "GET", "/api/v1/quests?size=100", null, null, null).statusCode())
                .isEqualTo(200);
        assertThat(send(anonymous, "GET", "/api/v1/quests?page=-1", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(anonymous, "GET", "/api/v1/quests?size=0", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(anonymous, "GET", "/api/v1/quests?size=101", null, null, null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void publicPagesAndDetailsExposeOnlyPublishedQuestsWithStableOrdering() throws Exception {
        Session owner = register();
        Session other = register();
        UUID firstPublished = publish(owner, "First published quest");
        UUID secondPublished = publish(owner, "Second published quest");
        UUID draft = createDraft(owner, "A private draft quest");
        UUID archived = publish(owner, "An archived quest");
        String archivedEtag = etag(getOwned(owner, archived));
        HttpResponse<String> archivedResult = action(owner, "archive", archived, archivedEtag);
        assertThat(archivedResult.statusCode()).isEqualTo(200);

        HttpClient anonymous = client();
        HttpResponse<String> all = send(anonymous, "GET", "/api/v1/quests", null, null, null);
        assertThat(all.statusCode()).isEqualTo(200);
        JsonNode page = json.readTree(all.body());
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        List<JsonNode> quests = new ArrayList<>();
        page.get("content").forEach(quests::add);
        List<String> actualOrder = quests.stream().map(quest -> quest.get("id").asText()).toList();
        List<String> expectedOrder = quests.stream()
                .sorted(Comparator
                        .comparing((JsonNode quest) -> Instant.parse(quest.get("createdAt").asText()))
                        .reversed()
                        .thenComparing(quest -> quest.get("id").asText(), Comparator.reverseOrder()))
                .map(quest -> quest.get("id").asText())
                .toList();
        assertThat(actualOrder).containsExactlyElementsOf(expectedOrder);
        assertThat(actualOrder).containsExactlyInAnyOrder(firstPublished.toString(), secondPublished.toString());

        JsonNode page0 = json.readTree(send(anonymous, "GET", "/api/v1/quests?page=0&size=1", null, null, null).body());
        JsonNode page1 = json.readTree(send(anonymous, "GET", "/api/v1/quests?page=1&size=1", null, null, null).body());
        JsonNode page2 = json.readTree(send(anonymous, "GET", "/api/v1/quests?page=2&size=1", null, null, null).body());
        assertThat(page0.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("hasNext").asBoolean()).isTrue();
        assertThat(page1.get("hasPrevious").asBoolean()).isTrue();
        assertThat(page2.get("content").size()).isZero();
        assertThat(page2.get("totalElements").asLong()).isEqualTo(2);

        HttpResponse<String> publicDetail = send(anonymous, "GET",
                "/api/v1/quests/" + firstPublished, null, null, null);
        assertThat(publicDetail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(publicDetail.body()).get("status").asText()).isEqualTo("PUBLISHED");
        assertThat(publicDetail.headers().firstValue("etag")).isPresent();

        assertThat(send(anonymous, "GET", "/api/v1/quests/" + draft, null, null, null).statusCode())
                .isEqualTo(404);
        assertThat(send(anonymous, "GET", "/api/v1/quests/" + archived, null, null, null).statusCode())
                .isEqualTo(404);
        assertThat(send(other.client(), "GET", "/api/v1/quests/" + draft, null, null, null).statusCode())
                .isEqualTo(404);
        assertThat(send(owner, "GET", "/api/v1/quests/" + draft, null, null).statusCode()).isEqualTo(200);
        assertThat(send(owner, "GET", "/api/v1/quests/" + archived, null, null).statusCode()).isEqualTo(200);

        HttpResponse<String> malformedId = send(anonymous, "GET", "/api/v1/quests/not-a-uuid", null, null, null);
        assertThat(malformedId.statusCode()).isEqualTo(400);
        assertThat(json.readTree(malformedId.body()).get("code").asText()).isEqualTo("invalid_parameter");
        assertThat(send(anonymous, "GET", "/api/v1/quests/" + UUID.randomUUID(), null, null, null).statusCode())
                .isEqualTo(404);
    }

    private UUID publish(Session owner, String title) throws Exception {
        HttpResponse<String> created = send(owner, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", title,
                "description", "A complete description for this quest.",
                "category", "ADVENTURE",
                "difficulty", "EASY",
                "rewardXp", 100)), null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(json.readTree(created.body()).get("id").asText());
        approveFixture(owner, id, etag(created));
        HttpResponse<String> published = action(owner, "publish", id, etag(created));
        assertThat(published.statusCode()).isEqualTo(200);
        return id;
    }

    private void approveFixture(Session owner, UUID id, String etag) throws Exception {
        assertThat(action(owner, "submit-review", id, etag).statusCode()).isEqualTo(202);
        var review = moderation.findById(id).orElseThrow();
        review.approve(java.time.Instant.now());
        moderation.saveAndFlush(review);
    }

    private UUID createDraft(Session owner, String title) throws Exception {
        HttpResponse<String> created = send(owner, "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", title)), null);
        assertThat(created.statusCode()).isEqualTo(201);
        return UUID.fromString(json.readTree(created.body()).get("id").asText());
    }

    private HttpResponse<String> getOwned(Session owner, UUID id) throws Exception {
        return send(owner, "GET", "/api/v1/me/quests/" + id, null, null);
    }

    private HttpResponse<String> action(Session owner, String action, UUID id, String etag) throws Exception {
        return send(owner, "POST", "/api/v1/quests/" + id + "/" + action, null, etag);
    }

    private HttpResponse<String> send(
            Session session, String method, String path, String body, String etag) throws Exception {
        String csrf = method.equals("GET") ? null : csrf(session.client());
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

    private Session register() throws Exception {
        HttpClient client = client();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String body = json.writeValueAsString(Map.of(
                "email", username + "@example.test",
                "username", username,
                "password", "exact password 🔒 value"));
        HttpResponse<String> response = send(client, "POST", "/api/v1/auth/register", body, csrf(client), null);
        assertThat(response.statusCode()).isEqualTo(201);
        return new Session(client);
    }

    private String csrf(HttpClient client) throws Exception {
        HttpResponse<String> response = send(client, "GET", "/api/v1/auth/csrf", null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("csrfToken").asText();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private static String etag(HttpResponse<String> response) {
        return response.headers().firstValue("etag").orElseThrow();
    }

    private record Session(HttpClient client) { }
}
