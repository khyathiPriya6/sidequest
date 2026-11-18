package com.sidequest.identity.api;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded, per-process fixed windows. Never trust client-supplied forwarding headers. */
@Component
public class AuthRateLimiter {
    private record Window(long until, int attempts) { }
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int ipLimit, accountLimit, capacity;
    private final long windowMillis;
    public AuthRateLimiter(Clock clock, @Value("${sidequest.auth.throttle.ip-limit:60}") int ipLimit,
            @Value("${sidequest.auth.throttle.account-limit:10}") int accountLimit,
            @Value("${sidequest.auth.throttle.capacity:10000}") int capacity,
            @Value("${sidequest.auth.throttle.window:15m}") Duration window) {
        if (ipLimit < 1 || accountLimit < 1 || capacity < 2 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Invalid authentication throttling configuration");
        }
        this.clock = clock; this.ipLimit = ipLimit; this.accountLimit = accountLimit;
        this.capacity = capacity; this.windowMillis = window.toMillis();
    }

    public synchronized void check(String ip, String account) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().until() <= now);
        increment("ip:" + ip, ipLimit, now);
        increment("account:" + account, accountLimit, now);
    }

    private void increment(String key, int limit, long now) {
        Window current = windows.get(key);
        if (current == null && windows.size() >= capacity || current != null && current.attempts() >= limit) {
            throw new AuthFailure(429, "rate_limited", "Too many authentication attempts. Try again later.");
        }
        windows.put(key, current == null ? new Window(now + windowMillis, 1)
                : new Window(current.until(), current.attempts() + 1));
    }
}
