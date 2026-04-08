package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository for {@link User} entities.
 * Extends {@link JpaRepository} which auto-generates CRUD operations at runtime.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Checks whether a user with the given username or email exists. */
    boolean existsByUsernameOrEmail(String username, String email);

    /** Finds a user whose username or email matches. Returns the first match. */
    Optional<User> findByUsernameOrEmail(String username, String email);

    /** Finds a user by exact username match. */
    Optional<User> findByUsername(String username);

}
