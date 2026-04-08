package com.example.demo.dto.request;

import com.example.demo.model.AuthMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Request to link an additional authentication method to an existing account")
public class LinkUserAuthMethodRequestDTO {

    @NotBlank
    @Schema(description = "Password for the new auth method", example = "securePassword123")
    private String password;

    @NotNull
    @Schema(description = "The authentication method to link", example = "KEYCLOAK")
    private AuthMethod authMethod;

}
