package com.example.demo.dto.request;

import com.example.demo.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Registration data for a new user")
public class RegisterRequestDTO {

    @NotBlank
    @Schema(description = "Username", example = "john_doe")
    private String username;

    @NotBlank
    @Email
    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @NotBlank
    @Size(min = 8)
    @Schema(description = "Password (minimum 8 characters)", example = "securePassword123")
    private String password;

    public User toEntity() {
        User user = new User();
        user.setUsername(this.username);
        user.setEmail(this.email);
        user.setPassword(this.password);
        return user;
    }

}
