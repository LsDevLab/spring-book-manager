package com.example.demo.controller;

import com.example.demo.dto.request.UserBookUpdateDTO;
import com.example.demo.dto.response.UserBookResponseDTO;
import com.example.demo.dto.response.UserStatsDTO;
import com.example.demo.service.UserBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/user/{userId}/books")
@RequiredArgsConstructor
class UserBookController {

    private final UserBookService userBookService;

    /**
     * GET /api/user/{userId}/books — returns the user's full reading list.
     *
     * @param userId the user's ID
     * @return 200 OK with list of {@link UserBookResponseDTO}, or 404 if user not found
     */
    @GetMapping
    public ResponseEntity<List<UserBookResponseDTO>> getReadingList(@PathVariable UUID userId) {
        return ResponseEntity.ok(
                userBookService.getReadingList(userId).stream()
                        .map(UserBookResponseDTO::fromEntity)
                        .toList()
        );
    }

    @GetMapping("/search")
    public ResponseEntity<Page<UserBookResponseDTO>> searchReadingList(
            @PathVariable UUID userId,
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
    public ResponseEntity<UserBookResponseDTO> addToReadingList(
            @PathVariable UUID userId,
            @PathVariable UUID bookId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserBookResponseDTO.fromEntity(
                        userBookService.addToReadingList(userId, bookId)
                ));
    }
    /**
     * PATCH /api/user/{userId}/books/{bookId} — partially updates reading progress.
     *
     * <p>Only non-null fields in the request body are applied (partial update pattern).
     * Status changes auto-set timestamps: READING → startedAt, COMPLETED → completedAt.</p>
     *
     * @param dto partial update fields (all optional)
     * @return 200 OK with updated {@link UserBookResponseDTO}, or 404 if entry not found
     */
    @PatchMapping("/{bookId}")
    public ResponseEntity<UserBookResponseDTO> updateStatus(
            @PathVariable UUID userId,
            @PathVariable UUID bookId,
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
    public ResponseEntity<Void> removeFromReadingList(
            @PathVariable UUID userId,
            @PathVariable UUID bookId) {
        userBookService.removeFromReadingList(userId, bookId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/user/{userId}/stats — returns reading statistics for a user.
     *
     * @return 200 OK with {@link UserStatsDTO}, or 404 if user not found
     */
    @GetMapping("/stats")
    public ResponseEntity<UserStatsDTO> getUserStats(@PathVariable UUID userId) {
        return ResponseEntity.ok(userBookService.getUserStats(userId));
    }


}
