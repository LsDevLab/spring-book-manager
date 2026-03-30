package com.example.demo.controller;

import com.example.demo.dto.request.UserBookUpdateDTO;
import com.example.demo.dto.response.UserBookResponseDTO;
import com.example.demo.dto.response.UserStatsDTO;
import com.example.demo.service.UserBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for a user's reading list — nested under {@code /api/user/{userId}/books}.
 *
 * <h3>Endpoints:</h3>
 * <table>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/user/{userId}/books</td><td>Get user's full reading list</td></tr>
 *   <tr><td>POST</td><td>/api/user/{userId}/books/{bookId}</td><td>Add book to reading list</td></tr>
 *   <tr><td>PATCH</td><td>/api/user/{userId}/books/{bookId}</td><td>Update progress/status/notes</td></tr>
 *   <tr><td>DELETE</td><td>/api/user/{userId}/books/{bookId}</td><td>Remove book from reading list</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/users/{userId}/books")
@PreAuthorize("hasRole('ADMIN') or authentication.details.userId.equals(#userId)")
@RequiredArgsConstructor
@Tag(name = "Reading List", description = "User's personal reading list management")
@SecurityRequirement(name = "bearerAuth")
class UserBookController {

    private final UserBookService userBookService;

    /**
     * GET /api/user/{userId}/books — returns the user's full reading list.
     *
     * @param userId the user's ID
     * @return 200 OK with list of {@link UserBookResponseDTO}, or 404 if user not found
     */
    @GetMapping
    @Operation(summary = "Get user's full reading list")
    @ApiResponse(responseCode = "200", description = "Reading list returned")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only access own reading list")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<List<UserBookResponseDTO>> getReadingList(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        return ResponseEntity.ok(
                userBookService.getReadingList(userId).stream()
                        .map(UserBookResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/search")
    @Operation(summary = "Search user's reading list with pagination")
    @ApiResponse(responseCode = "200", description = "Paginated reading list results")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only access own reading list")
    public ResponseEntity<Page<UserBookResponseDTO>> searchReadingList(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                userBookService.searchReadingList(userId, pageable)
                        .map(UserBookResponseDTO::fromEntity)
        );
    }

    /**
     * POST /api/user/{userId}/books/{bookId} — adds a book to the user's reading list.
     *
     * <p>Initial status is set to {@code WANT_TO_READ} with currentPage = 0.</p>
     *
     * @return 201 Created with {@link UserBookResponseDTO}, or 404 if user/book not found
     */
    @PostMapping("/{bookId}")
    @Operation(summary = "Add a book to the reading list")
    @ApiResponse(responseCode = "201", description = "Book added to reading list")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only modify own reading list")
    @ApiResponse(responseCode = "404", description = "User or book not found")
    public ResponseEntity<UserBookResponseDTO> addToReadingList(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Book ID") @PathVariable UUID bookId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserBookResponseDTO.fromEntity(
                        userBookService.addToReadingList(userId, bookId)
                ));
    }
    /**
     * PATCH /api/user/{userId}/books/{bookId} — partially updates reading progress.
     *
     * <p>Only non-null fields in the request body are applied (partial update pattern).
     * Status changes auto-set timestamps: READING -> startedAt, COMPLETED -> completedAt.</p>
     *
     * @param dto partial update fields (all optional)
     * @return 200 OK with updated {@link UserBookResponseDTO}, or 404 if entry not found
     */
    @PatchMapping("/{bookId}")
    @Operation(summary = "Update reading progress/status/notes", description = "Partial update — only non-null fields are applied. Status changes auto-set timestamps.")
    @ApiResponse(responseCode = "200", description = "Reading entry updated")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only modify own reading list")
    @ApiResponse(responseCode = "404", description = "Reading list entry not found")
    public ResponseEntity<UserBookResponseDTO> updateStatus(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Book ID") @PathVariable UUID bookId,
            @RequestBody UserBookUpdateDTO dto) {
        return ResponseEntity.ok(
                UserBookResponseDTO.fromEntity(
                        userBookService.updateUserBook(userId, bookId, dto)
                ));
    }

    /**
     * DELETE /api/user/{userId}/books/{bookId} — removes a book from the user's reading list.
     *
     * @return 204 No Content, or 404 if entry not found
     */
    @DeleteMapping("/{bookId}")
    @Operation(summary = "Remove a book from the reading list")
    @ApiResponse(responseCode = "204", description = "Book removed from reading list")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only modify own reading list")
    @ApiResponse(responseCode = "404", description = "Reading list entry not found")
    public ResponseEntity<Void> removeFromReadingList(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Book ID") @PathVariable UUID bookId) {
        userBookService.removeFromReadingList(userId, bookId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/user/{userId}/stats — returns reading statistics for a user.
     *
     * @return 200 OK with {@link UserStatsDTO}, or 404 if user not found
     */
    @GetMapping("/stats")
    @Operation(summary = "Get user's reading statistics", description = "Returns total books, completed, reading, and total pages read")
    @ApiResponse(responseCode = "200", description = "User statistics returned")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only access own stats")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<UserStatsDTO> getUserStats(
            @Parameter(description = "User ID") @PathVariable UUID userId) {
        return ResponseEntity.ok(userBookService.getUserStats(userId));
    }


}
