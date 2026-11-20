package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.sidequest.quest.*;
import jakarta.persistence.EntityManager;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.config.import=", "spring.jpa.properties.hibernate.generate_statistics=true"})
@ActiveProfiles("local")
public abstract class ParticipationApiSupport {
    @LocalServerPort protected int port;
    @Autowired protected ObjectMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ParticipationService service;
    @Autowired protected QuestRepository quests;
    @Autowired protected ParticipationRepository participations;
    @Autowired protected EntityManager em;
    @Autowired protected PlatformTransactionManager transactions;

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

    protected Session register() throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String username = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        var result = send(client, "POST", "/api/v1/auth/register", json.writeValueAsString(Map.of(
                "email", username + "@example.test", "username", username, "password", "a safe test password")), csrf(client), null);
        assertThat(result.statusCode()).isEqualTo(201);
        return new Session(client, UUID.fromString(body(result).get("id").asText()), csrf(client));
    }
    protected String csrf(HttpClient client) throws Exception {
        return body(send(client, "GET", "/api/v1/auth/csrf", null, null, null)).get("csrfToken").asText();
    }
    protected QuestData quest(Session creator, boolean publish) throws Exception {
        var created = request(creator, "POST", "/api/v1/quests", json.writeValueAsString(Map.of(
                "title", "A participation test quest", "description", "A complete description for this quest.",
                "category", "ADVENTURE", "difficulty", "HARD", "rewardXp", 175)));
        assertThat(created.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(body(created).get("id").asText());
        String etag = created.headers().firstValue("etag").orElseThrow();
        if (publish) {
            var published = send(creator.client, "POST", "/api/v1/quests/" + id + "/publish", null, creator.csrf, etag);
            assertThat(published.statusCode()).isEqualTo(200);
            etag = published.headers().firstValue("etag").orElseThrow();
        }
        return new QuestData(id, etag);
    }
    protected HttpResponse<String> archive(Session owner, QuestData quest) throws Exception {
        return send(owner.client, "POST", "/api/v1/quests/" + quest.id + "/archive", null, owner.csrf, quest.etag);
    }
    protected HttpResponse<String> join(Session player, UUID quest) throws Exception {
        return request(player, "POST", "/api/v1/quests/" + quest + "/join", null);
    }
    protected HttpResponse<String> request(Session session, String method, String path, String body) throws Exception {
        return send(session.client, method, path, body, session.csrf, null);
    }
    protected HttpResponse<String> send(HttpClient client, String method, String path, String body, String csrf, String etag) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(10));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (etag != null) builder.header("If-Match", etag);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    protected JsonNode body(HttpResponse<String> response) throws Exception { return json.readTree(response.body()); }
    protected TransactionTemplate tx() { var tx = new TransactionTemplate(transactions); tx.setTimeout(10); return tx; }
    protected static void await(CountDownLatch latch) {
        try { assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
    }
    protected void awaitDatabaseLockWait() throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < end) {
            if (jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'", Long.class) > 0) return;
            Thread.sleep(20);
        }
        fail("Expected a real PostgreSQL lock wait");
    }
    protected record Session(HttpClient client, UUID id, String csrf) { }
    protected record QuestData(UUID id, String etag) { }
}
