package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository for {@link User} entities.
 * Extends {@link JpaRepository} which auto-generates CRUD operations at runtime.
 */
public interface UserRepository extends JpaRepository<User, UUID> {
}
