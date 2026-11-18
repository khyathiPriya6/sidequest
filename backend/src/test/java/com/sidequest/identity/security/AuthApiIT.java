package com.sidequest.identity.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sidequest.identity.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
class AuthApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) { SecurityIT.database(registry); }

    private class Browser {
        Cookie cookie;
        String token;
        void csrf() throws Exception {
            var request = get("/api/v1/auth/csrf");
            if (cookie != null) request.cookie(cookie);
            var response = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
            if (response.getCookie("__Host-id") != null) cookie = response.getCookie("__Host-id");
            token = json.readTree(response.getContentAsString()).get("csrfToken").asText();
        }
        ResultActions postJson(String route, Object body) throws Exception {
            var result = mvc.perform(post("/api/v1/auth/" + route).cookie(cookie).header("X-CSRF-TOKEN", token)
                    .contentType("application/json").content(json.writeValueAsString(body)));
            var next = result.andReturn().getResponse().getCookie("__Host-id");
            if (next != null) cookie = next;
            return result;
        }
    }

    @Test void realRegistrationLoginRefreshAndLogout() throws Exception {
        Browser browser = new Browser(); browser.csrf();
        String oldCookie = browser.cookie.getValue();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String email = username + "@example.test";
        String password = " exact Unicode 🔒 password ";
        var registered = browser.postJson("register", Map.of("email", " " + email.toUpperCase() + " ", "username", username.toUpperCase(), "password", password))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/auth/me"))
                .andExpect(jsonPath("$.email").value(email)).andExpect(jsonPath("$.username").value(username)).andReturn();
        assertThat(browser.cookie.getValue()).isNotEqualTo(oldCookie);
        var output = json.readTree(registered.getResponse().getContentAsString());
        assertThat(output.size()).isEqualTo(3);
        assertThat(users.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("{argon2}$argon2id$").doesNotContain(password);
        mvc.perform(get("/api/v1/auth/me").cookie(browser.cookie)).andExpect(status().isOk());
        browser.csrf();
        Cookie loggedIn = browser.cookie;
        browser.postJson("logout", Map.of()).andExpect(status().isNoContent());
        assertThat(browser.cookie.getMaxAge()).isZero();
        mvc.perform(get("/api/v1/auth/me").cookie(loggedIn)).andExpect(status().isUnauthorized());
        browser = new Browser(); browser.csrf();
        browser.postJson("login", Map.of("email", email, "password", password)).andExpect(status().isOk());
        browser.csrf();
        browser.postJson("logout", Map.of()).andExpect(status().isNoContent());
        browser = new Browser(); browser.csrf();
        browser.postJson("login", Map.of("email", email, "password", password.strip())).andExpect(status().isUnauthorized());
    }

    @Test void invalidInputDuplicatesAndCredentialsUseSafeProblems() throws Exception {
        Browser browser = new Browser(); browser.csrf();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String email = username + "@example.test";
        var body = Map.of("email", email, "username", username, "password", "twelve plus characters");
        browser.postJson("register", body).andExpect(status().isCreated()); browser.csrf();
        browser.postJson("register", body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("account_conflict"))
                .andExpect(jsonPath("$.errors").doesNotExist());
        browser.postJson("register", Map.of("email", "another" + email, "username", username, "password", "twelve plus characters"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("account_conflict"));
        browser.postJson("register", Map.of("email", "invalid", "username", "bad!", "password", "short"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors").isArray());
        browser.postJson("register", Map.of("email", "valid@example.test", "username", "validname", "password", "🔒".repeat(11)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0].field").value("password"));
        browser.postJson("login", Map.of("email", email, "password", "wrong password value"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthenticated"));
        browser.postJson("login", Map.of("email", "missing@example.test", "password", "wrong password value"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("unauthenticated"));
        browser.postJson("register", Map.of("email", email, "username", username, "password", "x".repeat(9000)))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/v1/auth/register").cookie(browser.cookie).header("X-CSRF-TOKEN", browser.token)
                .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("malformed_request"));
        browser.postJson("login", Map.of("email", email, "password", "twelve plus characters", "role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test void disabledAndInvalidSessionsCannotAuthenticate() throws Exception {
        Browser browser = new Browser(); browser.csrf();
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String email = username + "@example.test";
        browser.postJson("register", Map.of("email", email, "username", username, "password", "twelve plus characters"))
                .andExpect(status().isCreated());
        var user = users.findByEmail(email).orElseThrow(); user.disable(); users.saveAndFlush(user);
        mvc.perform(get("/api/v1/auth/me").cookie(browser.cookie)).andExpect(status().isUnauthorized());
        browser = new Browser(); browser.csrf();
        browser.postJson("login", Map.of("email", email, "password", "twelve plus characters"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Authentication is required."));
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("__Host-id", "not-a-valid-session")))
                .andExpect(status().isUnauthorized());
    }

    @Test void accountThrottlingAppliesAcrossLoginAndRegistration() throws Exception {
        Browser browser = new Browser(); browser.csrf();
        String email = UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 10; i++) {
            browser.postJson("login", Map.of("email", email, "password", "wrong password value"))
                    .andExpect(status().isUnauthorized());
        }
        browser.postJson("register", Map.of("email", email, "username", "ratelimited", "password", "twelve plus characters"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("rate_limited"));
    }
}
