package com.sidequest.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String canonicalEmail);
    Optional<User> findByUsername(String canonicalUsername);
}
