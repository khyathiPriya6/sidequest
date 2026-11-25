package com.sidequest.identity.security;

import com.sidequest.identity.AccountStatus;
import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class SecurityConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }

    @Bean PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2",
                new Argon2PasswordEncoder(16, 32, 1, 19456, 2)));
    }

    @Bean AuthenticationManager authenticationManager(UserRepository users, PasswordEncoder encoder) {
        String dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
        return request -> {
            String canonical;
            try { canonical = User.normalizeEmail(request.getName()); }
            catch (IllegalArgumentException exception) { canonical = ""; }
            var user = users.findByEmail(canonical).orElse(null);
            boolean matches = encoder.matches(String.valueOf(request.getCredentials()),
                    user == null ? dummyHash : user.getPasswordHash());
            if (!matches || user == null || user.getStatus() != AccountStatus.ACTIVE) {
                throw new BadCredentialsException("Invalid credentials");
            }
            return authenticated(new UserPrincipal(user.getId()));
        };
    }

    public static UsernamePasswordAuthenticationToken authenticated(UserPrincipal principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Bean CookieSerializer cookieSerializer(Environment environment) {
        boolean local = List.of(environment.getActiveProfiles()).contains("local");
        if (local && environment.getActiveProfiles().length != 1) {
            throw new IllegalStateException("The local HTTP profile cannot be combined with deployment profiles");
        }
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName(local ? "sidequest-local-id" : "__Host-id");
        serializer.setUseSecureCookie(!local);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookiePath("/");
        return serializer;
    }

    @Bean CsrfTokenRepository csrfTokenRepository() {
        var repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ApiSecurityErrors errors,
            CsrfTokenRepository csrf, SecurityContextRepository contexts,
            SessionAuthenticationStrategy strategy, UserRepository users,
            JdbcIndexedSessionRepository sessions, Clock clock) throws Exception {
        http.formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
            .securityContext(context -> context.securityContextRepository(contexts))
            .sessionManagement(session -> session.sessionAuthenticationStrategy(strategy))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .exceptionHandling(config -> config
                .authenticationEntryPoint((request, response, exception) -> errors.write(request, response, 401, "unauthenticated"))
                .accessDeniedHandler((request, response, exception) -> errors.write(request, response, 403,
                    exception instanceof CsrfException ? "csrf_invalid" : "forbidden")))
            .authorizeHttpRequests(routes -> routes
                .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/auth/csrf", "/api/v1/quests", "/api/v1/quests/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/leaderboard", "/api/v1/me/leaderboard-rank").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/me/social/settings", "/api/v1/social/profiles/*").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/me/social/settings").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/moderation/queue", "/api/v1/moderation/legacy").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/moderation/quests/*/decisions", "/api/v1/moderation/legacy/*/intake", "/api/v1/quests/*/reports", "/api/v1/me/quests/*/appeals").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/me", "/api/v1/me/quests", "/api/v1/me/quests/*", "/api/v1/me/quests/*/moderation", "/api/v1/me/participations", "/api/v1/me/participations/*", "/api/v1/me/profile", "/api/v1/me/achievements", "/api/v1/me/xp-transactions").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout", "/api/v1/quests", "/api/v1/quests/*/publish", "/api/v1/quests/*/archive", "/api/v1/quests/*/submit-review", "/api/v1/quests/*/join", "/api/v1/participations/*/complete").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/quests/*").authenticated()
                .anyRequest().denyAll())
            .addFilterAfter(new SessionBoundaryFilter(users, sessions, clock), SecurityContextHolderFilter.class);
        return http.build();
    }
}
