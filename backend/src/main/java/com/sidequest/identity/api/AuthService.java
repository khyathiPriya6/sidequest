package com.sidequest.identity.api;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AuthService(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }

    public static boolean validPasswordLength(String password) {
        return password != null && password.codePointCount(0, password.length()) >= 12
                && password.codePointCount(0, password.length()) <= 128;
    }

    @Transactional
    public AuthDtos.CurrentUser register(AuthDtos.Registration request) {
        if (!validPasswordLength(request.password())) {
            throw new AuthFailure(422, "password_length", "Password must contain 12–128 Unicode code points.");
        }
        var user = User.create(request.email(), request.username(), encoder.encode(request.password()));
        return AuthDtos.CurrentUser.from(users.saveAndFlush(user));
    }

    @Transactional(readOnly = true)
    public AuthDtos.CurrentUser current(UUID id) {
        return users.findById(id).map(AuthDtos.CurrentUser::from)
                .orElseThrow(() -> new AuthFailure(401, "unauthenticated", "Authentication is required."));
    }
}
