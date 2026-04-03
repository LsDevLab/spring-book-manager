package com.example.demo.controller;

import com.example.demo.dto.response.ActiveReaderDTO;
import com.example.demo.model.Topic;
import com.example.demo.service.ReadingActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Public endpoints — no authentication required (configured in SecurityConfiguration).
// All data comes from Redis, zero database queries.
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Live reading activity feed (public)")
public class ActivityController {

    private final ReadingActivityService readingActivityService;

    // "Who's reading BACKEND books right now?"
    // Sorted by most recently active (Sorted Set score = timestamp).
    @GetMapping("/topic/{topic}")
    @Operation(summary = "Active readers by topic", description = "Returns users currently reading books in a given topic, ordered by most recently active")
    @ApiResponse(responseCode = "200", description = "List of active readers")
    public ResponseEntity<List<ActiveReaderDTO>> activeReadersByTopic(
            @Parameter(description = "Book topic", required = true)
            @PathVariable Topic topic,
            @Parameter(description = "Max number of results")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(readingActivityService.activeReadersByTopic(topic, limit));
    }

    // "Who's reading Clean Code right now?"
    // Unordered (comes from a Set, not a Sorted Set).
    @GetMapping("/book/{bookId}")
    @Operation(summary = "Active readers of a book", description = "Returns users currently reading a specific book")
    @ApiResponse(responseCode = "200", description = "List of active readers")
    public ResponseEntity<List<ActiveReaderDTO>> activeReadersByBook(
            @Parameter(description = "Book ID", required = true)
            @PathVariable UUID bookId) {
        return ResponseEntity.ok(readingActivityService.activeReadersByBook(bookId));
    }

    // "How many people are reading Clean Code?"
    // Single O(1) Redis call (SCARD).
    @GetMapping("/book/{bookId}/count")
    @Operation(summary = "Active reader count for a book", description = "Returns the number of users currently reading a specific book")
    @ApiResponse(responseCode = "200", description = "Reader count")
    public ResponseEntity<Long> activeReadersCount(
            @Parameter(description = "Book ID", required = true)
            @PathVariable UUID bookId) {
        return ResponseEntity.ok(readingActivityService.activeReadersByBookCount(bookId));
    }
}