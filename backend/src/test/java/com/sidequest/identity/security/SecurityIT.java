package com.sidequest.identity.security;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@Import(SecurityIT.Fixtures.class)
class SecurityIT {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired AuthenticationManager authenticationManager;
    org.springframework.session.FindByIndexNameSessionRepository<org.springframework.session.Session> sessions;
    @Autowired
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sessionInterface(JdbcIndexedSessionRepository repository) {
        sessions = (org.springframework.session.FindByIndexNameSessionRepository) repository;
    }
    @Autowired JdbcIndexedSessionRepository jdbcSessions;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Use scripts/test-postgres.sh");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test void publicAndProtectedRoutesAndCsrfErrors() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("unauthenticated"));
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("csrf_invalid"));
        mvc.perform(post("/api/v1/auth/register")).andExpect(status().isForbidden());
        mvc.perform(get("/not-allowed")).andExpect(status().isUnauthorized());
    }

    @Test void passwordVerificationKeepsCredentialsOutOfPrincipal() {
        String password = "Exact unicode 🔒 password ";
        String hash = encoder.encode(password);
        assertThat(hash).startsWith("{argon2}$argon2id$");
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password.strip(), hash)).isFalse();
        User user = users.saveAndFlush(User.create(UUID.randomUUID() + "@example.test", uniqueName(), hash));
        var result = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), password));
        assertThat(result.getPrincipal()).isEqualTo(new UserPrincipal(user.getId()));
        assertThat(result.getCredentials()).isNull();
        assertThatThrownBy(() -> authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        user.disable(); users.saveAndFlush(user);
        assertThatThrownBy(() -> authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), password)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test void sessionRotatesPersistsExpiresAndRevokesDisabledAccounts() throws Exception {
        User user = users.saveAndFlush(User.create(UUID.randomUUID() + "@example.test", uniqueName(), encoder.encode("test password for account")));
        var bootstrap = mvc.perform(get("/api/v1/auth/csrf")).andExpect(status().isOk()).andReturn();
        var cookie = bootstrap.getResponse().getCookie("__Host-id");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue(); assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/"); assertThat(cookie.getDomain()).isNull();
        assertThat(bootstrap.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
        String csrf = bootstrap.getResponse().getContentAsString();
        var login = mvc.perform(post("/api/v1/auth/login").cookie(cookie).header("X-CSRF-TOKEN", csrf).param("id", user.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        var authenticatedCookie = login.getResponse().getCookie("__Host-id");
        assertThat(authenticatedCookie.getValue()).isNotEqualTo(cookie.getValue());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(authenticatedCookie)).andExpect(status().isOk())
                .andExpect(content().string(user.getId().toString()));
        mvc.perform(get("/not-allowed").cookie(authenticatedCookie)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").cookie(authenticatedCookie).header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isForbidden());
        var session = sessions.findByPrincipalName(user.getId().toString()).values().iterator().next();
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
        session.setAttribute(SessionBoundaryFilter.AUTHENTICATED_AT, Instant.now().minus(Duration.ofHours(12)).toEpochMilli());
        sessions.save(session);
        mvc.perform(get("/api/v1/auth/me").cookie(authenticatedCookie)).andExpect(status().isUnauthorized());
        // A second authenticated session is revoked by the account-state check.
        var second = sessions.createSession();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityConfiguration.authenticated(new UserPrincipal(user.getId())));
        second.setAttribute("SPRING_SECURITY_CONTEXT", context);
        second.setAttribute(SessionBoundaryFilter.AUTHENTICATED_AT, Instant.now().toEpochMilli());
        sessions.save(second);
        user.disable(); users.saveAndFlush(user);
        String encoded = java.util.Base64.getEncoder().encodeToString(second.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/v1/auth/me").cookie(new jakarta.servlet.http.Cookie("__Host-id", encoded)))
                .andExpect(status().isUnauthorized());
        assertThat(sessions.findByPrincipalName(user.getId().toString())).isEmpty();
    }

    @Test void idleExpiredSessionsAreRejectedAndCleaned() {
        var session = sessions.createSession();
        session.setAttribute("test", "non-secret");
        session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(31)));
        sessions.save(session);
        assertThat(sessions.findById(session.getId())).isNull();
        jdbcSessions.cleanUpExpiredSessions();
        assertThat(sessions.findById(session.getId())).isNull();
    }

    private static String uniqueName() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    // Test-only endpoints exercise the real security/session boundary; never part of the runtime artifact.
    @TestConfiguration static class Fixtures { @Bean Probe probe(SessionAuthentication auth) { return new Probe(auth); } }
    @RestController static class Probe {
        private final SessionAuthentication auth;
        Probe(SessionAuthentication auth) { this.auth = auth; }
        @GetMapping("/api/v1/auth/csrf") String csrf(CsrfToken token) { return token.getToken(); }
        @PostMapping("/api/v1/auth/login") void login(@RequestParam UUID id, HttpServletRequest request, HttpServletResponse response) {
            auth.establish(SecurityConfiguration.authenticated(new UserPrincipal(id)), request, response);
        }
        @GetMapping("/api/v1/auth/me") String me() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    }
}
