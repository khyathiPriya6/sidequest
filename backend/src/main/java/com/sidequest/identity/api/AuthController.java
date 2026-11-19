package com.sidequest.identity.api;

import com.sidequest.identity.security.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final AuthRateLimiter limiter;
    private final AuthenticationManager manager;
    private final SessionAuthentication sessions;
    private final CookieSerializer cookies;
    public AuthController(AuthService service, AuthRateLimiter limiter, AuthenticationManager manager,
            SessionAuthentication sessions, CookieSerializer cookies) {
        this.service = service; this.limiter = limiter; this.manager = manager; this.sessions = sessions; this.cookies = cookies;
    }

    @GetMapping("/csrf") AuthDtos.Csrf csrf(CsrfToken token) { return new AuthDtos.Csrf(token.getToken()); }

    @PostMapping("/register") ResponseEntity<AuthDtos.CurrentUser> register(@Valid @RequestBody AuthDtos.Registration body,
            HttpServletRequest request, HttpServletResponse response) {
        limiter.check(request.getRemoteAddr(), body.email());
        var user = service.register(body);
        sessions.establish(SecurityConfiguration.authenticated(new UserPrincipal(user.id())), request, response);
        return ResponseEntity.created(URI.create("/api/v1/auth/me")).body(user);
    }

    @PostMapping("/login") AuthDtos.CurrentUser login(@Valid @RequestBody AuthDtos.Login body,
            HttpServletRequest request, HttpServletResponse response) {
        limiter.check(request.getRemoteAddr(), body.email());
        if (!AuthService.validPasswordLength(body.password())) throw new BadCredentialsException("Invalid credentials");
        var authentication = manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        sessions.establish(authentication, request, response);
        return service.current(((UserPrincipal) authentication.getPrincipal()).userId());
    }

    @GetMapping("/me") AuthDtos.CurrentUser me(@AuthenticationPrincipal UserPrincipal principal) {
        return service.current(principal.userId());
    }

    @PostMapping("/logout") ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        var cookie = new CookieSerializer.CookieValue(request, response, "");
        cookie.setCookieMaxAge(0);
        cookies.writeCookieValue(cookie);
        return ResponseEntity.noContent().build();
    }
}
