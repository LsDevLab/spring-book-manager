package com.example.demo.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity()
@Table(name = "user_auth_method", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "auth_type"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Tracks which authentication methods a user has registered/linked with")
public class UserAuthMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Schema(description = "Registration record ID")
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @Schema(description = "The user who owns this registration")
    private User user;

    @Enumerated(EnumType.STRING)
    @Schema(description = "Authentication method", example = "SELF")
    private AuthMethod authMethod;

    @Schema(description = "When this auth method was registered")
    private LocalDateTime registrationDate;

}
