package com.example.demo.controller;

import com.example.demo.dto.request.UserBookUpdateDTO;
import com.example.demo.dto.response.UserBookResponseDTO;
import com.example.demo.dto.response.UserStatsDTO;
import com.example.demo.interceptor.RateLimit;
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

@RestController
@RequestMapping("/api/users/{userId}/books")
@PreAuthorize("hasRole('ADMIN') or authentication.details.userId.equals(#userId)")
@RequiredArgsConstructor
@Tag(name = "Reading List", description = "User's personal reading list management")
@SecurityRequirement(name = "bearerAuth")
class UserBookController {

    private final UserBookService userBookService;

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

    @PostMapping("/{bookId}")
    @RateLimit(maxRequests = 1, windowSeconds = 30)
    @Operation(summary = "Add a book to the reading list")
    @ApiResponse(responseCode = "201", description = "Book added to reading list")
    @ApiResponse(responseCode = "403", description = "Not authorized — can only modify own reading list")
    @ApiResponse(responseCode = "404", description = "User or book not found")
    @ApiResponse(responseCode = "409", description = "Book already added to user reading list")
    public ResponseEntity<UserBookResponseDTO> addToReadingList(
            @Parameter(description = "User ID") @PathVariable UUID userId,
            @Parameter(description = "Book ID") @PathVariable UUID bookId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserBookResponseDTO.fromEntity(
                        userBookService.addToReadingList(userId, bookId)
                ));
    }
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
