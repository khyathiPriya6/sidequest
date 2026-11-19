package com.sidequest.identity.api;

import com.sidequest.identity.User;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() { }
    public record Registration(@NotBlank @Email @Size(max = 254) String email,
            @NotNull @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
            @NotNull @Size(max = 256) String password) {
        public Registration { if (email != null) email = email.strip().toLowerCase(Locale.ROOT); }
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotNull @Size(max = 256) String password) {
        public Login { if (email != null) email = email.strip().toLowerCase(Locale.ROOT); }
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record CurrentUser(UUID id, String email, String username) {
        public static CurrentUser from(User user) { return new CurrentUser(user.getId(), user.getEmail(), user.getUsername()); }
    }
    public record Csrf(String csrfToken) { }
}
