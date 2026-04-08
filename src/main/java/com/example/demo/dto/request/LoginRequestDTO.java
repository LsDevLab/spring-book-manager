package com.example.demo.dto.request;

import com.example.demo.model.AuthMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Login credentials")
public class LoginRequestDTO {

    @NotBlank
    @Schema(description = "Username", example = "john_doe")
    private String username;

    @NotBlank
    @Schema(description = "Password", example = "securePassword123")
    private String password;

    @NotBlank
    @Email
    @Schema(description = "Email address", example = "john@example.com")
    private String email;

    @NotNull
    @Schema(description = "The method of authentication", example = "KEYCLOAK")
    private AuthMethod authMethod;

}
