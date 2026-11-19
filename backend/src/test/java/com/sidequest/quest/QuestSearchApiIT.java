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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
class QuestSearchApiIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired QuestRepository quests;

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
        quests.deleteAllInBatch();
    }

    @Test
    void searchesLiteralCaseInsensitiveSubstringsAndCombinesFilters() throws Exception {
        Session owner = register();
        UUID percent = publish(owner, "Find 100% focus", "Practice mindful breathing today.", "CREATIVITY", "EASY");
        UUID underscore = publish(owner, "Build_a habit", "Practice daily focus without distraction.", "LEARNING", "HARD");
        publish(owner, "Learning to Bake", "Bake a fresh loaf with a guide.", "FOOD", "MEDIUM");
        UUID wildcardDecoy = publish(owner, "BuildXa habit", "A quiet cooking quest.", "LEARNING", "HARD");
        publish(owner, "Path C:\\Quest", "Follow a path through the old garden.", "ADVENTURE", "EASY");
        createDraft(owner, "Find 100% private quest");

        JsonNode percentResult = search("100%", null, null, 0, 20);
        assertThat(total(percentResult)).isEqualTo(1);
        assertThat(firstId(percentResult)).isEqualTo(percent.toString());

        JsonNode underscoreResult = search("Build_a", null, null, 0, 20);
        assertThat(total(underscoreResult)).isEqualTo(1);
        assertThat(firstId(underscoreResult)).isEqualTo(underscore.toString());
        assertThat(total(search("bAkE", null, null, 0, 20))).isEqualTo(1);
        assertThat(total(search("C:\\", null, null, 0, 20))).isEqualTo(1);
        assertThat(total(search("focus", "LEARNING", "HARD", 0, 20))).isEqualTo(1);
        assertThat(firstId(search("focus", "LEARNING", "HARD", 0, 20))).isEqualTo(underscore.toString());
        assertThat(total(search(null, "LEARNING", null, 0, 20))).isEqualTo(2);
        assertThat(total(search(null, "LEARNING", "HARD", 0, 20))).isEqualTo(2);
        assertThat(total(search("no matching quest", null, null, 0, 20))).isZero();
        assertThat(total(search("private", null, null, 0, 20))).isZero();
        assertThat(total(search("x' OR 1=1 --", null, null, 0, 20))).isZero();
        assertThat(total(search("", null, null, 0, 20))).isEqualTo(5);
        assertThat(total(search("   ", null, null, 0, 20))).isEqualTo(5);

        JsonNode firstPage = search("Build", "LEARNING", "HARD", 0, 1);
        JsonNode secondPage = search("Build", "LEARNING", "HARD", 1, 1);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(2);
        assertThat(secondPage.get("totalElements").asLong()).isEqualTo(2);
        assertThat(firstPage.get("hasNext").asBoolean()).isTrue();
        assertThat(secondPage.get("hasPrevious").asBoolean()).isTrue();
        assertThat(List.of(firstId(firstPage), firstId(secondPage)))
                .containsExactly(wildcardDecoy.toString(), underscore.toString());
    }

    @Test
    void enforcesSearchLengthAndExactFilterValues() throws Exception {
        HttpClient anonymous = client();
        String accepted = "🧭".repeat(120);
        HttpResponse<String> atLimit = send(anonymous, "GET", "/api/v1/quests?q=" + encode(accepted), null, null, null);
        assertThat(atLimit.statusCode()).isEqualTo(200);
        assertThat(total(json.readTree(atLimit.body()))).isZero();

        HttpResponse<String> tooLong = send(anonymous, "GET",
                "/api/v1/quests?q=" + encode(accepted + "x"), null, null, null);
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(json.readTree(tooLong.body()).get("code").asText()).isEqualTo("query_too_long");
        assertThat(send(anonymous, "GET", "/api/v1/quests?category=unknown", null, null, null).statusCode())
                .isEqualTo(400);
        assertThat(send(anonymous, "GET", "/api/v1/quests?difficulty=impossible", null, null, null).statusCode())
                .isEqualTo(400);
    }

    private JsonNode search(String q, String category, String difficulty, int page, int size) throws Exception {
        var parameters = new ArrayList<String>();
        if (q != null) parameters.add("q=" + encode(q));
        if (category != null) parameters.add("category=" + encode(category));
        if (difficulty != null) parameters.add("difficulty=" + encode(difficulty));
        parameters.add("page=" + page);
        parameters.add("size=" + size);
        HttpResponse<String> response = send(client(), "GET", "/api/v1/quests?" + String.join("&", parameters),
                null, null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    private UUID publish(Session owner, String title, String description, String category, String difficulty)
            throws Exception {
        HttpResponse<String> created = send(owner.client(), "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", title,
                "description", description,
                "category", category,
                "difficulty", difficulty,
                "rewardXp", 100)), csrf(owner.client()), null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(json.readTree(created.body()).get("id").asText());
        HttpResponse<String> published = send(owner.client(), "POST",
                "/api/v1/quests/" + id + "/publish", null, csrf(owner.client()),
                created.headers().firstValue("etag").orElseThrow());
        assertThat(published.statusCode()).isEqualTo(200);
        return id;
    }

    private void createDraft(Session owner, String title) throws Exception {
        HttpResponse<String> created = send(owner.client(), "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", title)), csrf(owner.client()), null);
        assertThat(created.statusCode()).isEqualTo(201);
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static long total(JsonNode page) {
        return page.get("totalElements").asLong();
    }

    private static String firstId(JsonNode page) {
        return page.get("content").get(0).get("id").asText();
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }

    private record Session(HttpClient client) { }
}
