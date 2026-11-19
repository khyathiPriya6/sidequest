package com.sidequest.identity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/** Shared by registration/login: explicit context save is required by Spring Security 6. */
@Service
public class SessionAuthentication {
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository contexts;
    private final Clock clock;
    public SessionAuthentication(SessionAuthenticationStrategy strategy, SecurityContextRepository contexts, Clock clock) {
        this.strategy = strategy; this.contexts = contexts; this.clock = clock;
    }
    public void establish(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        request.getSession();
        strategy.onAuthentication(authentication, request, response);
        request.getSession().setAttribute(SessionBoundaryFilter.AUTHENTICATED_AT, clock.millis());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
