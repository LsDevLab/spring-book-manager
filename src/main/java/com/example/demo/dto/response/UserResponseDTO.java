package com.example.demo.dto.response;

import com.example.demo.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response DTO for {@link com.example.demo.model.User} — controls which fields the client sees.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User details")
public class UserResponseDTO {

    @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    // Manual conversion — same approach as BookResponseDTO.fromEntity()
    public static UserResponseDTO fromEntity(User user) {
        return new UserResponseDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail()
        );
    }
}
