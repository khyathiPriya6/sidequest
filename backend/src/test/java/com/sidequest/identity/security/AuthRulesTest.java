package com.sidequest.identity.security;

import static org.assertj.core.api.Assertions.*;
import com.sidequest.identity.api.AuthRateLimiter;
import com.sidequest.identity.api.AuthService;
import java.time.*;
import org.junit.jupiter.api.Test;

class AuthRulesTest {
    @Test void measuresConfiguredHashEncodingWithoutLoggingCredentials() {
        var encoder = new SecurityConfiguration().passwordEncoder();
        long start = System.nanoTime();
        for (int i = 0; i < 5; i++) {
            String hash = encoder.encode("synthetic benchmark input");
            assertThat(encoder.matches("synthetic benchmark input", hash)).isTrue();
        }
        System.out.printf("Argon2id: mean encode+verify pair over 5 iterations: %.1f ms%n", (System.nanoTime() - start) / 5_000_000.0);
    }
    @Test void passwordBoundsCountCodePointsAndPreserveSpaces() {
        assertThat(AuthService.validPasswordLength("🔒".repeat(12))).isTrue();
        assertThat(AuthService.validPasswordLength("🔒".repeat(128))).isTrue();
        assertThat(AuthService.validPasswordLength("🔒".repeat(129))).isFalse();
        assertThat(AuthService.validPasswordLength("🔒".repeat(11))).isFalse();
        assertThat(AuthService.validPasswordLength(" ".repeat(12))).isTrue();
        assertThat(AuthService.validPasswordLength(null)).isFalse();
    }
    @Test void limiterBoundsIpAndMemoryAndExpiresWithoutLockingAccounts() {
        class MutableClock extends Clock {
            Instant now = Instant.EPOCH;
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        }
        var clock = new MutableClock();
        var limiter = new AuthRateLimiter(clock, 2, 10, 100, Duration.ofMinutes(15));
        limiter.check("ip", "a"); limiter.check("ip", "b");
        assertThatThrownBy(() -> limiter.check("ip", "c")).hasMessageContaining("Too many");
        clock.now = clock.now.plus(Duration.ofMinutes(15));
        assertThatCode(() -> limiter.check("ip", "c")).doesNotThrowAnyException();
        var bounded = new AuthRateLimiter(clock, 100, 100, 2, Duration.ofMinutes(15));
        bounded.check("one", "one");
        assertThatThrownBy(() -> bounded.check("two", "two")).hasMessageContaining("Too many");
    }
}
