package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * User entity — represents a developer on the platform.
 *
 * <p>Maps to the {@code users} table ("user" is a reserved word in PostgreSQL).
 * Username and email are unique and non-nullable at the DB level via {@code @Column} constraints.</p>
 *
 * <p>Introduced in Phase 3.1. Related to {@link Book} through the {@link UserBook} join entity.</p>
 */
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User entity representing a developer on the platform")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Column(unique = true, nullable = false)
    @Schema(description = "Unique username", example = "john_doe")
    private String username;

    @Column(unique = true, nullable = false)
    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @Column(nullable = false)
    @Schema(hidden = true)
    private String password;

    @Enumerated(EnumType.STRING)
    @Schema(description = "User role", example = "USER")
    private Role role;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Schema(description = "Authentication methods registered/linked by this user")
    private List<UserAuthMethod> authMethods = new ArrayList<>();



}
