package com.example.demo.controller;

import com.example.demo.dto.request.UserRequestDTO;
import com.example.demo.dto.response.UserResponseDTO;
import com.example.demo.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management")
@SecurityRequirement(name = "bearerAuth")
class UserController {

    private final UserService userService;

    // GET /api/users/me — returns the currently authenticated user's profile
    @GetMapping("/me")
    @Operation(summary = "Get current user's profile", description = "Returns the profile of the currently authenticated user")
    @ApiResponse(responseCode = "200", description = "User profile returned")
    public ResponseEntity<UserResponseDTO> me(Authentication authentication){
        return ResponseEntity.ok(
                UserResponseDTO.fromEntity(userService.getUserByUsername(authentication.getName()))
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users (ADMIN)")
    @ApiResponse(responseCode = "200", description = "List of all users returned")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers(){
        return ResponseEntity.ok(
                userService.getAllUsers().stream()
                        .map(UserResponseDTO::fromEntity).toList()
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Search users with pagination (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Paginated user results")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<Page<UserResponseDTO>> searchUsers(
            Pageable pageable
    ){
        return ResponseEntity.ok(
                userService.searchUsers(pageable)
                        .map(UserResponseDTO::fromEntity)
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get a user by ID (ADMIN)")
    @ApiResponse(responseCode = "200", description = "User found")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<UserResponseDTO> getUserById(@RequestParam UUID id){
        return ResponseEntity.ok(UserResponseDTO.fromEntity(userService.getUserById(id)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new user (ADMIN)")
    @ApiResponse(responseCode = "201", description = "User created")
    @ApiResponse(responseCode = "400", description = "Validation failed or user already exists")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<UserResponseDTO> postUser(@RequestBody @Valid UserRequestDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponseDTO.fromEntity(userService.createUser(dto.toEntity())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a user (ADMIN)")
    @ApiResponse(responseCode = "200", description = "User updated")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<UserResponseDTO> putUser(@RequestParam UUID id, @RequestBody @Valid UserRequestDTO dto){
        return ResponseEntity.ok()
                .body(UserResponseDTO.fromEntity(userService.updateUser(id, dto.toEntity())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a user (ADMIN)")
    @ApiResponse(responseCode = "204", description = "User deleted")
    @ApiResponse(responseCode = "404", description = "User not found")
    @ApiResponse(responseCode = "403", description = "Not authorized — ADMIN role required")
    public ResponseEntity<UserResponseDTO> deleteUser(@RequestParam UUID id){
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

}
