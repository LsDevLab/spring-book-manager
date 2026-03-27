package com.example.demo.dto.request;

import com.example.demo.model.Book;
import com.example.demo.model.User;
import jakarta.persistence.Column;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating or updating a {@link com.example.demo.model.User}.
 *
 * <p>Validated with Jakarta annotations: username must be 3–50 chars, email must be well-formed.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserRequestDTO {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Email
    private String email;

    public User toEntity() {
        User user = new User();
        user.setEmail(this.email);
        user.setUsername(this.username);
        return user;
    }

}
