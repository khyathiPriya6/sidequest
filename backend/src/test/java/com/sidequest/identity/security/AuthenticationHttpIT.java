package com.sidequest.identity.security;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
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
class AuthenticationHttpIT {
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SecurityIT.database(registry); }

    @Test void browserStyleCookieSessionWorksThroughActualHttpServer() throws Exception {
        var jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        var client = HttpClient.newBuilder().cookieHandler(jar).build();
        var csrf = send(client, "GET", "csrf", null, null);
        assertThat(csrf.statusCode()).isEqualTo(200);
        String token = json.readTree(csrf.body()).get("csrfToken").asText();
        String name = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String body = json.writeValueAsString(Map.of("email", name + "@example.test", "username", name, "password", "exact password 🔒 value"));
        var created = send(client, "POST", "register", token, body);
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.headers().firstValue("set-cookie").orElseThrow()).contains("sidequest-local-id=", "HttpOnly", "SameSite=Lax");
        assertThat(send(client, "GET", "me", null, null).statusCode()).isEqualTo(200);
        assertThat(send(client, "POST", "logout", token, "{}").statusCode()).isEqualTo(403);
        token = json.readTree(send(client, "GET", "csrf", null, null).body()).get("csrfToken").asText();
        assertThat(send(client, "POST", "logout", token, "{}").statusCode()).isEqualTo(204);
        assertThat(send(client, "GET", "me", null, null).statusCode()).isEqualTo(401);
        token = json.readTree(send(client, "GET", "csrf", null, null).body()).get("csrfToken").asText();
        String login = json.writeValueAsString(Map.of("email", name + "@example.test", "password", "exact password 🔒 value"));
        assertThat(send(client, "POST", "login", token, login).statusCode()).isEqualTo(200);
        assertThat(send(client, "GET", "me", null, null).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> send(HttpClient client, String method, String route, String csrf, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/" + route));
        if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
        if (body != null) builder.header("Content-Type", "application/json");
        return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
