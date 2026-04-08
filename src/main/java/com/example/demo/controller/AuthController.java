package com.example.demo.controller;

import com.example.demo.dto.request.LinkUserAuthMethodRequestDTO;
import com.example.demo.dto.request.LoginRequestDTO;
import com.example.demo.dto.request.RegisterRequestDTO;
import com.example.demo.dto.response.UserAuthMethodResponseDTO;
import com.example.demo.dto.response.LoginResponseDTO;
import com.example.demo.dto.response.UserBookResponseDTO;
import com.example.demo.security.AuthUserDetails;
import com.example.demo.service.AuthService;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication endpoints")
class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    @ApiResponse(responseCode = "200", description = "User registered successfully")
    @ApiResponse(responseCode = "400", description = "Username or email already taken, or validation failed")
    @ApiResponse(responseCode = "409", description = "User already registered")
    public ResponseEntity<UserAuthMethodResponseDTO> register(@RequestBody @Valid RegisterRequestDTO dto){
        return ResponseEntity.ok(UserAuthMethodResponseDTO.fromEntity(authService.registerUser(dto)));
    }

    @PostMapping("/auth-methods")
    @Operation(summary = "Link an authentication method to the logged user")
    @ApiResponse(responseCode = "200", description = "Linked authentication method successfully")
    @ApiResponse(responseCode = "409", description = "Auth method already linked")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<UserAuthMethodResponseDTO> linkMethod(
            @RequestBody @Valid LinkUserAuthMethodRequestDTO dto,
            Authentication authentication){
        return ResponseEntity.ok(
                UserAuthMethodResponseDTO.fromEntity(
                        authService.linkAuthMethodForUser(
                                dto, ((AuthUserDetails) Objects.requireNonNull(authentication.getDetails())).userId()
                        )
                )
        );
    }

    @GetMapping("/auth-methods")
    @Operation(summary = "Get the authentication methods for the logged user")
    @ApiResponse(responseCode = "200", description = "Authentications methods fetched successfully")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<UserAuthMethodResponseDTO>> getMethods(
            Authentication authentication) {
        return ResponseEntity.ok(
                userService.getUserById(
                    ((AuthUserDetails) Objects.requireNonNull(authentication.getDetails())).userId()
                ).getAuthMethods().stream()
                    .map(UserAuthMethodResponseDTO::fromEntity)
                    .toList()
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and get a JWT token")
    @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned")
    @ApiResponse(responseCode = "401", description = "Wrong credentials")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody @Valid LoginRequestDTO dto){
        return ResponseEntity.ok(authService.login(dto));
    }

}
