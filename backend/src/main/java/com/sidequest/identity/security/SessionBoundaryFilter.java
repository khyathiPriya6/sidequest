package com.sidequest.identity.security;

import com.sidequest.identity.AccountStatus;
import com.sidequest.identity.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionBoundaryFilter extends OncePerRequestFilter {
    static final String AUTHENTICATED_AT = "sidequest.authenticatedAt";
    private final UserRepository users;
    private final JdbcIndexedSessionRepository sessions;
    private final Clock clock;

    SessionBoundaryFilter(UserRepository users, JdbcIndexedSessionRepository sessions, Clock clock) {
        this.users = users; this.sessions = sessions; this.clock = clock;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            var session = request.getSession(false);
            Object started = session == null ? null : session.getAttribute(AUTHENTICATED_AT);
            boolean disabled = users.findById(principal.userId())
                    .map(user -> user.getStatus() != AccountStatus.ACTIVE).orElse(true);
            boolean expired = !(started instanceof Long time)
                    || clock.millis() - time >= Duration.ofHours(12).toMillis();
            if (disabled || expired) {
                if (disabled) {
                    sessions.findByPrincipalName(principal.getName()).keySet().forEach(sessions::deleteById);
                }
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
