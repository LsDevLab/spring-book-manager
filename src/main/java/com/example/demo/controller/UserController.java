package com.example.demo.controller;

import com.example.demo.dto.request.UserRequestDTO;
import com.example.demo.dto.response.UserResponseDTO;
import com.example.demo.model.User;
import com.example.demo.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for User CRUD operations.
 *
 * <h3>Endpoints:</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/users</td><td>List all users</td></tr>
 *   <tr><td>GET</td><td>/api/users/{id}</td><td>Get user by ID</td></tr>
 *   <tr><td>POST</td><td>/api/users</td><td>Create a new user</td></tr>
 *   <tr><td>PUT</td><td>/api/users/{id}</td><td>Update a user</td></tr>
 *   <tr><td>DELETE</td><td>/api/users/{id}</td><td>Delete a user</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
class UserController {

    private final UserService userService;

    // GET /api/users/me — returns the currently authenticated user's profile
    @GetMapping("/me")
    public ResponseEntity<UserResponseDTO> me(Authentication authentication){
        return ResponseEntity.ok(
                UserResponseDTO.fromEntity(userService.getUserByUsername(authentication.getName()))
        );
    }

    /**
     * GET /api/users — returns all users.
     *
     * @return 200 OK with list of {@link UserResponseDTO}
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponseDTO>> getAllUsers(){
        return ResponseEntity.ok(
                userService.getAllUsers().stream()
                        .map(UserResponseDTO::fromEntity).toList()
        );
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponseDTO>> searchUsers(
            Pageable pageable
    ){
        return ResponseEntity.ok(
                userService.searchUsers(pageable)
                        .map(UserResponseDTO::fromEntity)
        );
    }

    /**
     * GET /api/users/{id} — returns a single user.
     *
     * @return 200 OK with {@link UserResponseDTO}, or 404 if not found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> getUserById(@RequestParam UUID id){
        return ResponseEntity.ok(UserResponseDTO.fromEntity(userService.getUserById(id)));
    }

    /**
     * POST /api/users — creates a new user.
     *
     * @param dto validated request body (username 3–50 chars, valid email)
     * @return 201 Created with {@link UserResponseDTO}
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> postUser(@RequestBody @Valid UserRequestDTO dto){
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponseDTO.fromEntity(userService.createUser(dto.toEntity())));
    }

    /**
     * PUT /api/users/{id} — fully replaces a user's data.
     *
     * @return 200 OK with updated {@link UserResponseDTO}, or 404 if not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> putUser(@RequestParam UUID id, @RequestBody @Valid UserRequestDTO dto){
        return ResponseEntity.ok()
                .body(UserResponseDTO.fromEntity(userService.updateUser(id, dto.toEntity())));
    }

    /**
     * DELETE /api/users/{id} — deletes a user.
     *
     * @return 204 No Content, or 404 if not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> deleteUser(@RequestParam UUID id){
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

}
