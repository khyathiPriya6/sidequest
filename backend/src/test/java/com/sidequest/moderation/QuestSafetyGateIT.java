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
import java.time.Instant;
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
class QuestSafetyGateIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired QuestModerationRepository reviews;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Use scripts/test-postgres.sh for a new isolated database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test
    void draftNeedsOwnerReviewAndCannotBePublicOrJoinedUntilApproved() throws Exception {
        Session owner = register();
        Session participant = register();
        HttpClient guest = client();
        HttpResponse<String> created = send(owner.client, "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", "Walk in a local park",
                        "description", "Take a safe daytime walk in your local park.",
                        "category", "FITNESS", "difficulty", "EASY", "rewardXp", 50)), owner.csrf, null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(body(created).get("id").asText());
        String etag = etag(created);

        assertThat(body(send(owner.client, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null, null))
                .get("state").asText()).isEqualTo("UNREVIEWED");
        assertThat(send(guest, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null, null)
                .statusCode()).isEqualTo(401);
        assertThat(send(participant.client, "GET", "/api/v1/me/quests/" + id + "/moderation", null, null, null)
                .statusCode()).isEqualTo(404);
        assertThat(send(participant.client, "POST", "/api/v1/quests/" + id + "/submit-review", null,
                participant.csrf, etag).statusCode()).isEqualTo(404);
        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/publish", null,
                owner.csrf, etag).statusCode()).isEqualTo(409);

        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/submit-review", null,
                null, etag).statusCode()).isEqualTo(403);
        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/submit-review", null,
                owner.csrf, null).statusCode()).isEqualTo(428);
        var submitted = send(owner.client, "POST", "/api/v1/quests/" + id + "/submit-review", null,
                owner.csrf, etag);
        assertThat(submitted.statusCode()).isEqualTo(202);
        assertThat(body(submitted).get("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/submit-review", null,
                owner.csrf, etag).statusCode()).isEqualTo(202);
        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/publish", null,
                owner.csrf, etag).statusCode()).isEqualTo(409);
        assertThat(send(guest, "GET", "/api/v1/quests/" + id, null, null, null).statusCode()).isEqualTo(404);
        assertThat(body(send(guest, "GET", "/api/v1/quests?q=Walk%20in%20a%20local%20park", null, null, null))
                .get("content").size()).isZero();
        assertThat(send(participant.client, "POST", "/api/v1/quests/" + id + "/join", null,
                participant.csrf, null).statusCode()).isEqualTo(404);

        // Until SAFETY-04 there is intentionally no public moderator endpoint.
        approveFixture(id);
        var published = send(owner.client, "POST", "/api/v1/quests/" + id + "/publish", null,
                owner.csrf, etag);
        assertThat(published.statusCode()).isEqualTo(200);
        assertThat(send(guest, "GET", "/api/v1/quests/" + id, null, null, null).statusCode()).isEqualTo(200);
        assertThat(body(send(guest, "GET", "/api/v1/quests?q=Walk%20in%20a%20local%20park", null, null, null))
                .get("content").findValuesAsText("id")).contains(id.toString());
        var joined = send(participant.client, "POST", "/api/v1/quests/" + id + "/join", null,
                participant.csrf, null);
        assertThat(joined.statusCode()).isEqualTo(201);
        var participationId = body(joined).get("id").asText();

        hideFixture(id);
        // Idempotent publish must not claim success once the public Quest is hidden.
        assertThat(send(owner.client, "POST", "/api/v1/quests/" + id + "/publish",
                null, owner.csrf, etag(published)).statusCode()).isEqualTo(409);
        assertThat(send(guest, "GET", "/api/v1/quests/" + id, null, null, null).statusCode()).isEqualTo(404);
        assertThat(body(send(guest, "GET", "/api/v1/quests?q=Walk%20in%20a%20local%20park", null, null, null))
                .get("content").size()).isZero();
        Session newcomer = register();
        assertThat(send(newcomer.client, "POST", "/api/v1/quests/" + id + "/join", null,
                newcomer.csrf, null).statusCode()).isEqualTo(404);
        assertThat(send(participant.client, "GET", "/api/v1/me/participations/" + participationId,
                null, null, null).statusCode()).isEqualTo(200);
        // Existing participation can complete under the current E04 rules.
        assertThat(send(participant.client, "POST", "/api/v1/participations/" + participationId + "/complete",
                null, participant.csrf, null).statusCode()).isEqualTo(200);
    }

    @Test
    void unsafeSignalsPrioritizeReviewWithoutPunitiveAutoRejectionOrAutomaticApproval() throws Exception {
        Session creator = register();
        var created = send(creator.client, "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", "Dangerous road challenge",
                        "description", "Race through traffic at high speed on a public road.",
                        "category", "ADVENTURE", "difficulty", "HARD", "rewardXp", 100)),
                creator.csrf, null);
        UUID id = UUID.fromString(body(created).get("id").asText());
        var submitted = send(creator.client, "POST", "/api/v1/quests/" + id + "/submit-review",
                null, creator.csrf, etag(created));
        assertThat(submitted.statusCode()).isEqualTo(202);
        assertThat(body(submitted).get("state").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(body(submitted).get("reasonCode").asText()).isEqualTo("PHYSICAL_DANGER");

        var patch = send(creator.client, "PATCH", "/api/v1/quests/" + id,
                json.writeValueAsString(Map.of("description", "Take a relaxing walk with an adult companion.")),
                creator.csrf, etag(created));
        assertThat(patch.statusCode()).isEqualTo(200);
        assertThat(body(send(creator.client, "GET", "/api/v1/me/quests/" + id + "/moderation",
                null, null, null)).get("state").asText()).isEqualTo("UNREVIEWED");
    }

    @Test
    void aRejectedDraftNeedsRevisionBeforeResubmission() throws Exception {
        Session creator = register();
        var created = send(creator.client, "POST", "/api/v1/quests",
                json.writeValueAsString(Map.of("title", "Learn watercolor painting",
                        "description", "Paint a simple landscape at home.",
                        "category", "CREATIVITY", "difficulty", "EASY", "rewardXp", 25)),
                creator.csrf, null);
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(body(created).get("id").asText());
        String initialTag = etag(created);
        assertThat(send(creator.client, "POST", "/api/v1/quests/" + id + "/submit-review",
                null, creator.csrf, initialTag).statusCode()).isEqualTo(202);

        var record = reviews.findById(id).orElseThrow();
        record.reject(Instant.now(), "OTHER");
        reviews.saveAndFlush(record);

        assertThat(send(creator.client, "POST", "/api/v1/quests/" + id + "/submit-review",
                null, creator.csrf, initialTag).statusCode()).isEqualTo(409);
        assertThat(send(creator.client, "POST", "/api/v1/quests/" + id + "/publish",
                null, creator.csrf, initialTag).statusCode()).isEqualTo(409);

        var updated = send(creator.client, "PATCH", "/api/v1/quests/" + id,
                json.writeValueAsString(Map.of("description",
                        "Paint a peaceful landscape using watercolor supplies at home.")),
                creator.csrf, initialTag);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(body(send(creator.client, "GET", "/api/v1/me/quests/" + id + "/moderation",
                null, null, null)).get("state").asText()).isEqualTo("UNREVIEWED");
        assertThat(send(creator.client, "POST", "/api/v1/quests/" + id + "/submit-review",
                null, creator.csrf, etag(updated)).statusCode()).isEqualTo(202);
    }

    private void approveFixture(UUID id) {
        var review = reviews.findById(id).orElseThrow();
        review.approve(Instant.now());
        reviews.saveAndFlush(review);
    }
    private void hideFixture(UUID id) {
        var review = reviews.findById(id).orElseThrow();
        review.hide(Instant.now(), "DANGEROUS_ACT");
        reviews.saveAndFlush(review);
    }
    private Session register() throws Exception {
        HttpClient browser = client();
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String token = csrf(browser);
        var created = send(browser, "POST", "/api/v1/auth/register",
                json.writeValueAsString(Map.of("email", username + "@example.test",
                        "username", username, "password", "Safe test password 2026!")),
                token, null);
        assertThat(created.statusCode()).isEqualTo(201);
        return new Session(browser, csrf(browser));
    }
    private String csrf(HttpClient browser) throws Exception {
        return body(send(browser, "GET", "/api/v1/auth/csrf", null, null, null))
                .get("csrfToken").asText();
    }
    private JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    private HttpResponse<String> send(HttpClient browser, String method, String route,
            String body, String token, String etag) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route));
        if (token != null) request.header("X-CSRF-TOKEN", token);
        if (etag != null) request.header("If-Match", etag);
        if (body != null) request.header("Content-Type", "application/json");
        return browser.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String etag(HttpResponse<String> result) { return result.headers().firstValue("etag").orElseThrow(); }
    private HttpClient client() { return HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private record Session(HttpClient client, String csrf) { }
}
