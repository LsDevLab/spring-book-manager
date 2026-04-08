package com.example.demo.dto.response;

import com.example.demo.model.AuthMethod;
import com.example.demo.model.User;
import com.example.demo.model.UserAuthMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Response after successfully linking an authentication method")
public class UserAuthMethodResponseDTO {

    @Schema(description = "The newly linked authentication method", example = "KEYCLOAK")
    private AuthMethod authMethod;

    @Schema(description = "When the method was linked")
    private LocalDateTime registrationDate;

    public static UserAuthMethodResponseDTO fromEntity(UserAuthMethod userAuthMethod) {
        return new UserAuthMethodResponseDTO(
                userAuthMethod.getAuthMethod(),
                userAuthMethod.getRegistrationDate()
        );
    }

}