package com.example.demo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    private Role role;


}
