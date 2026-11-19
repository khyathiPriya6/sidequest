package com.sidequest.identity.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/** Only stable identity is retained in the session; never credentials or the User entity. */
public record UserPrincipal(UUID userId) implements Principal, Serializable {
    @Override public String getName() { return userId.toString(); }
}
